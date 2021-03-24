/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v2

import akka.{actor, Done}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.persistence.jdbc.config.SnapshotConfig
import akka.persistence.jdbc.db.SlickExtension
import akka.persistence.jdbc.journal.dao.AkkaSerialization
import akka.persistence.jdbc.snapshot.dao
import akka.persistence.jdbc.snapshot.dao.legacy.{ByteArraySnapshotSerializer, SnapshotQueries}
import akka.persistence.jdbc.snapshot.dao.legacy.SnapshotTables.{SnapshotRow => OldSnapshotRow}
import akka.persistence.jdbc.snapshot.dao.SnapshotTables.SnapshotRow
import akka.serialization.Serialization
import akka.stream.scaladsl.Source
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabasePublisher
import slick.jdbc.{JdbcBackend, JdbcProfile, ResultSetConcurrency, ResultSetType}
import slick.jdbc.PostgresProfile.api._
import slickProfile.api._

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

/**
 * Migrates the legacy snapshot data into the new snapshot table
 *
 * @param system the actor system
 * @param profile the jdbc profile
 * @param serialization the akka serialization
 * @param pageSize number of records to bulk process together
 */
case class MigrateSnapshot(system: ActorSystem[_],
                           profile: JdbcProfile,
                           serialization: Serialization,
                           pageSize: Int = 1000
) {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  implicit val ec: ExecutionContextExecutor = system.executionContext
  implicit val classicSys: actor.ActorSystem = system.toClassic

  private val snapshotConfig: SnapshotConfig = new SnapshotConfig(
    system.settings.config.getConfig("jdbc-snapshot-store")
  )

  private val queries = new SnapshotQueries(profile, snapshotConfig.legacySnapshotTableConfiguration)
  private val newQueries = new dao.SnapshotQueries(profile, snapshotConfig.snapshotTableConfiguration)
  private val serializer: ByteArraySnapshotSerializer =
    new ByteArraySnapshotSerializer(serialization)
  private val snapshotdb: JdbcBackend.Database =
    SlickExtension(system).database(system.settings.config.getConfig("jdbc-snapshot-store")).database

  /**
   * Write the state snapshot data into the new snapshot table applying the proper serialization
   */
  def migrate(): Unit = {
    // create a table query from the old journal
    val query = queries.SnapshotTable.result
      .withStatementParameters(
        rsType = ResultSetType.ForwardOnly,
        rsConcurrency = ResultSetConcurrency.ReadOnly,
        fetchSize = pageSize
      )
      .transactionally

    // use above query to build a database publisher of legacy "SnapshotRow"
    val dbPublisher: DatabasePublisher[OldSnapshotRow] = snapshotdb.stream(query)

    val streamFuture: Future[Done] = Source
      .fromPublisher(dbPublisher)
      // group into fetchsize to use all records in memory
      .grouped(pageSize)
      // convert to new snapshot type
      .map(records => records.map(convertSnapshot))
      // for each "page", write to the new table
      .mapAsync[Unit](1)(records => {

        val stmt = records
          .map(newQueries.insertOrUpdate)
          .asInstanceOf[Seq[DBIO[Unit]]]
          .foldLeft[DBIO[Unit]](DBIO.successful[Unit] {})((priorStmt, newStmt) => {
            priorStmt.andThen(newStmt)
          })
          .withPinnedSession
          .transactionally

        snapshotdb.run(stmt)
      })
      .run()

    Await.result(streamFuture, Duration.Inf)
  }

  /**
   * converts the old snapshot to the new one
   *
   * @param old prior snapshot in old format
   * @return new snapshot in new format
   */
  private def convertSnapshot(old: OldSnapshotRow): SnapshotRow = {
    val transformed: Try[SnapshotRow] = serializer
      .deserialize(old)
      .flatMap({ case (meta, snapshot) =>
        val serializedMetadata: Option[AkkaSerialization.AkkaSerialized] = meta.metadata
          .flatMap(m => AkkaSerialization.serialize(serialization, m).toOption)

        AkkaSerialization
          .serialize(serialization, payload = snapshot)
          .map(serializedSnapshot =>
            SnapshotRow(
              meta.persistenceId,
              meta.sequenceNr,
              meta.timestamp,
              serializedSnapshot.serId,
              serializedSnapshot.serManifest,
              serializedSnapshot.payload,
              serializedMetadata.map(_.serId),
              serializedMetadata.map(_.serManifest),
              serializedMetadata.map(_.payload)
            )
          )
      })

    transformed match {
      case Failure(e)      => throw e
      case Success(output) => output
    }
  }
}
