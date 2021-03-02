/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v2

import akka.actor.typed.ActorSystem
import akka.persistence.jdbc.snapshot.dao.legacy.{ByteArraySnapshotSerializer, SnapshotQueries}
import akka.persistence.jdbc.snapshot.dao.legacy.SnapshotTables.{SnapshotRow => OldSnapshotRow}
import akka.persistence.jdbc.snapshot.dao.SnapshotTables.SnapshotRow
import akka.persistence.SnapshotMetadata
import slick.jdbc.PostgresProfile.api._
import slickProfile.api._

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import slick.jdbc.ResultSetType
import slick.jdbc.ResultSetConcurrency
import slick.basic.DatabasePublisher
import akka.stream.scaladsl.Source
import akka.persistence.jdbc.journal.dao.AkkaSerialization
import scala.util.Try
import akka.persistence.jdbc.snapshot.dao

case class MigrateSnapshot(system: ActorSystem[_]) extends Migrate {


  final val log: Logger = LoggerFactory.getLogger(getClass)

  private val queries = new SnapshotQueries(profile, snapshotConfig.legacySnapshotTableConfiguration)

  private val newQueries = new dao.SnapshotQueries(profile, snapshotConfig.snapshotTableConfiguration)

  private val serializer: ByteArraySnapshotSerializer =
    new ByteArraySnapshotSerializer(serialization)

  /**
   * Write the state snapshot data into the new snapshot table applying the proper serialization
   */
  def run(): Unit = {
    val fetchSize: Int = 10000

    // create a table query from the old journal
    val query = queries.SnapshotTable.result
      .withStatementParameters(
        rsType = ResultSetType.ForwardOnly,
        rsConcurrency = ResultSetConcurrency.ReadOnly,
        fetchSize = fetchSize
      )
      .transactionally

    // use above query to build a database publisher of legacy "SnapshotRow"
    val dbPublisher: DatabasePublisher[OldSnapshotRow] = snapshotdb.stream(query)

    val streamFuture = Source.fromPublisher(dbPublisher)
      // group into fetchsize to use all records in memory
      .grouped(fetchSize)
      // convert to new snapshot type
      .map(records => records.map(convertSnapshot))
      // for each "page", write to the new table
      .runForeach((records: Seq[SnapshotRow]) => {
        // create a bunch of insert statements
        val inserts: Seq[Option[DBIOAction[Int, NoStream, Effect.Write]]] = records
          .map(newQueries.insertOrUpdate)
          .map(x => Option(x))

        // reduce to a single insert and run it
        inserts.foldLeft[Option[DBIOAction[Int, NoStream, Effect.Write]]](None)((agg, someInsert) => {
          agg match {
            case None => someInsert
            case Some(prior) => someInsert.map(_.andThen(prior))
          }
        })
        .map(_.withPinnedSession.transactionally)
        .map(snapshotdb.run)
      })

    Await.result(streamFuture, Duration.Inf)
  }

  /**
    * converts the old snapshot to the new one
    *
    * @param old
    * @return
    */
  private def convertSnapshot(old: OldSnapshotRow): SnapshotRow = {
    val transformed: Try[SnapshotRow] = serializer
      .deserialize(old)
      .flatMap({
        case (meta, snapshot) =>
          val serializedMetadata = meta.metadata
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
                serializedMetadata.map(_.payload)))
      })

      transformed match {
        case Failure(e) => throw e
        case Success(output) => output
      }
  }
}
