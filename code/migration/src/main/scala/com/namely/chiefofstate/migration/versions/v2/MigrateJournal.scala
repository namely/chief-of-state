/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v2

import akka.{actor, Done}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.persistence.PersistentRepr
import akka.persistence.jdbc.config.{JournalConfig, ReadJournalConfig}
import akka.persistence.jdbc.db.SlickExtension
import akka.persistence.jdbc.journal.dao.{legacy, AkkaSerialization, JournalQueries}
import akka.persistence.jdbc.journal.dao.legacy.ByteArrayJournalSerializer
import akka.persistence.jdbc.journal.dao.JournalTables.{JournalAkkaSerializationRow, TagRow}
import akka.persistence.jdbc.query.dao.legacy.ReadJournalQueries
import akka.serialization.Serialization
import akka.stream.scaladsl.Source
import org.slf4j.{Logger, LoggerFactory}
import slick.dbio.Effect
import slick.jdbc.{JdbcBackend, JdbcProfile, ResultSetConcurrency, ResultSetType}
import slick.jdbc.PostgresProfile.api._
import slick.sql.FixedSqlAction
import slickProfile.api._

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

/**
 * Migrate the old legacy journal data to the new journal table
 *
 * @param system the actor system
 * @param profile the jdbc profile
 * @param serialization the akka serialization
 * @param pageSize number of records to write at once
 */
case class MigrateJournal(system: ActorSystem[_],
                          profile: JdbcProfile,
                          serialization: Serialization,
                          pageSize: Int = 1000
) {
  implicit val ec: ExecutionContextExecutor = system.executionContext
  implicit val classicSys: actor.ActorSystem = system.toClassic
  final val log: Logger = LoggerFactory.getLogger(getClass)

  // the journal, read journal and snapshot config
  private val journalConfig: JournalConfig = new JournalConfig(system.settings.config.getConfig("jdbc-journal"))
  private val readJournalConfig: ReadJournalConfig = new ReadJournalConfig(
    system.settings.config.getConfig("jdbc-read-journal")
  )
  // the various databases
  private val journaldb: JdbcBackend.Database =
    SlickExtension(system).database(system.settings.config.getConfig("jdbc-read-journal")).database

  private val queries: ReadJournalQueries = new ReadJournalQueries(profile, readJournalConfig)
  private val serializer: ByteArrayJournalSerializer =
    new ByteArrayJournalSerializer(serialization, readJournalConfig.pluginConfig.tagSeparator)

  private val newJournalQueries: JournalQueries =
    new JournalQueries(profile, journalConfig.eventJournalTableConfiguration, journalConfig.eventTagTableConfiguration)

  private val schemaName: String = journalConfig.eventJournalTableConfiguration.schemaName match {
    case Some(schema) => schema
    case None         => throw new Exception("missing schema name in configuration")
  }

  /**
   * write all legacy events into the new journal tables applying the proper serialization.
   * The migration will be done by batchSize. That will avoid to pull all records into memory
   */
  def run(): Unit = {
    val query: DBIOAction[Seq[legacy.JournalRow], Streaming[legacy.JournalRow], Effect.Read with Effect.Transactional] =
      queries.JournalTable.result
        .withStatementParameters(
          rsType = ResultSetType.ForwardOnly,
          rsConcurrency = ResultSetConcurrency.ReadOnly,
          fetchSize = pageSize
        )
        .transactionally

    val pipeline: Future[Done] = Source
      // build source from table query
      .fromPublisher(journaldb.stream(query))
      // deserialize it
      .via(serializer.deserializeFlow)
      .map({
        case Success((repr, tags, ordering)) => (repr, tags, ordering)
        case Failure(exception)              => throw exception // blow-up on failure
      })
      // generate new repr and new tags as tuples of (<newRepr>, <newTags>)
      .map({ case (repr, tags, ordering) => serialize(repr, tags, ordering) })
      // get pages of many records at once
      .grouped(pageSize)
      .mapAsync(1)(records => {
        val stmt: DBIO[Unit] = records
          // get all the sql statements for this record as an option
          .map({ case (newRepr, newTags) => writeJournalRowsStatements(newRepr, newTags) })
          // reduce to 1 statement
          .foldLeft[DBIO[Unit]](DBIO.successful[Unit] {})((priorStmt, nextStmt) => {
            priorStmt.andThen(nextStmt)
          })

        journaldb.run(stmt)
      })
      .run()

    val eventualUnit: Future[Unit] = for {
      _ <- pipeline
      _ <- setNextOrderingValue()
    } yield ()

    Await.result(eventualUnit, Duration.Inf)
  }

  /**
   * returns the next ordering value
   */
  private[versions] def nextOrderingValue(): Long = {
    val legacyTableName: String = s"$schemaName.journal"

    val eventualLong: Future[Long] = for {
      seqName: String <- journaldb.run(
        sql"""SELECT pg_get_serial_sequence($legacyTableName, 'ordering')""".as[String].head
      )
      nextVal <- journaldb.run(sql""" SELECT pg_catalog.nextval($seqName);""".as[Long].head)
    } yield nextVal

    Await.result(eventualLong, Duration.Inf)
  }

  /**
   *  sets the next ordering value
   */
  private[versions] def setNextOrderingValue(): Future[Long] = {
    val tableName: String = s"$schemaName.event_journal"
    val nextVal: Long = nextOrderingValue()

    for {
      sequenceName: String <- journaldb.run(
        sql"""SELECT pg_get_serial_sequence($tableName, 'ordering')""".as[String].head
      )
      value <- journaldb.run(sql""" SELECT pg_catalog.setval($sequenceName, $nextVal, false)""".as[Long].head)
    } yield value
  }

  /**
   *  serialize the PersistentRepr and construct a JournalAkkaSerializationRow and set of matching tags
   *
   * @param persistentRepr the PersistentRepr
   * @param tags the set of tags that match the given persistentRepr
   * @param ordering the ordering of the PersistentRepr
   * @return the tuple of JournalAkkaSerializationRow and set of tags
   */
  private[versions] def serialize(
    persistentRepr: PersistentRepr,
    tags: Set[String],
    ordering: Long
  ): (JournalAkkaSerializationRow, Set[String]) = {

    val serializedPayload: AkkaSerialization.AkkaSerialized =
      AkkaSerialization.serialize(serialization, persistentRepr.payload) match {
        case Failure(exception) => throw exception
        case Success(value)     => value
      }

    val serializedMetadata: Option[AkkaSerialization.AkkaSerialized] =
      persistentRepr.metadata.flatMap(m => AkkaSerialization.serialize(serialization, m).toOption)
    val row: JournalAkkaSerializationRow = JournalAkkaSerializationRow(
      ordering,
      persistentRepr.deleted,
      persistentRepr.persistenceId,
      persistentRepr.sequenceNr,
      persistentRepr.writerUuid,
      persistentRepr.timestamp,
      persistentRepr.manifest,
      serializedPayload.payload,
      serializedPayload.serId,
      serializedPayload.serManifest,
      serializedMetadata.map(_.payload),
      serializedMetadata.map(_.serId),
      serializedMetadata.map(_.serManifest)
    )

    (row, tags)
  }

  private[versions] def writeJournalRowsStatements(
    journalSerializedRow: JournalAkkaSerializationRow,
    tags: Set[String]
  ): DBIO[Unit] = {
    val journalInsert: DBIO[Long] = newJournalQueries.JournalTable
      .returning(newJournalQueries.JournalTable.map(_.ordering))
      .forceInsert(journalSerializedRow)

    val tagInserts: FixedSqlAction[Option[Int], NoStream, Effect.Write] =
      newJournalQueries.TagTable ++= tags
        .map(tag => TagRow(journalSerializedRow.ordering, tag))
        .toSeq

    journalInsert.flatMap(_ => tagInserts.asInstanceOf[DBIO[Unit]])
  }
}
