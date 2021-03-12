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
import akka.persistence.journal.Tagged
import akka.serialization.Serialization
import akka.stream.scaladsl.{Sink, Source}
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
 */
case class MigrateJournal(system: ActorSystem[_], profile: JdbcProfile, serialization: Serialization) {
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

  // The default value per akka reference conf is 500
  private val bufferSize: Int = journalConfig.daoConfig.bufferSize

  private val schemaName: String = journalConfig.eventJournalTableConfiguration.schemaName match {
    case Some(schema) => schema
    case None => throw Exception("missing schema name in configuration")
  }

  /**
   * write all legacy events into the new journal tables applying the proper serialization.
   * The migration will be done by batchSize. That will avoid to pull all records into memory
   *
   * @param fetchSize the number of records to fetch
   */
  def migrateWithBatchSize(fetchSize: Int = bufferSize): Unit = {
    // get the parallelism setting. This value in the akka reference conf is 8
    val parallelism: Int = journalConfig.daoConfig.parallelism

    val query: DBIOAction[Seq[legacy.JournalRow], Streaming[legacy.JournalRow], Effect.Read with Effect.Transactional] =
      queries.JournalTable.result
        .withStatementParameters(
          rsType = ResultSetType.ForwardOnly,
          rsConcurrency = ResultSetConcurrency.ReadOnly,
          fetchSize = fetchSize
        )
        .transactionally

    val eventualDone: Future[Done] = Source
      .fromPublisher(journaldb.stream(query))
      .via(serializer.deserializeFlow)
      .mapAsync(parallelism)(reprAndOrdNr => Future.fromTry(reprAndOrdNr))
      .map { case (repr, tags, ordering) => repr.withPayload(Tagged(repr, tags)) -> ordering }
      .mapAsync(parallelism) { case (repr, ordering) =>
        // serializing the PersistentRepr using the same ordering received from the old journal
        val (row, tags): (JournalAkkaSerializationRow, Set[String]) = serialize(repr, ordering)
        // persist the data
        writeJournalRows(row, tags)
      }
      .runWith(Sink.ignore)

    // run the data migration and set the next ordering value
    val eventualUnit: Future[Unit] = for {
      _ <- eventualDone
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
   * @param pr the PersistentRepr
   * @param ordering the ordering of the PersistentRepr
   * @return the tuple of JournalAkkaSerializationRow and set of tags
   */
  private[versions] def serialize(pr: PersistentRepr, ordering: Long): (JournalAkkaSerializationRow, Set[String]) = {

    val (updatedPr, tags) = pr.payload match {
      case Tagged(payload, tags) => (pr.withPayload(payload), tags)
      case _                     => (pr, Set.empty[String])
    }

    val serializedPayload: AkkaSerialization.AkkaSerialized =
      AkkaSerialization.serialize(serialization, updatedPr.payload) match {
        case Failure(exception) => throw exception
        case Success(value)     => value
      }

    val serializedMetadata: Option[AkkaSerialization.AkkaSerialized] =
      updatedPr.metadata.flatMap(m => AkkaSerialization.serialize(serialization, m).toOption)
    val row: JournalAkkaSerializationRow = JournalAkkaSerializationRow(
      ordering,
      updatedPr.deleted,
      updatedPr.persistenceId,
      updatedPr.sequenceNr,
      updatedPr.writerUuid,
      updatedPr.timestamp,
      updatedPr.manifest,
      serializedPayload.payload,
      serializedPayload.serId,
      serializedPayload.serManifest,
      serializedMetadata.map(_.payload),
      serializedMetadata.map(_.serId),
      serializedMetadata.map(_.serManifest)
    )

    (row, tags)
  }

  /**
   * inserts a serialized journal row with the mapping tags
   *
   * @param journalSerializedRow the serialized journal row
   * @param tags the set of tags
   */
  private[versions] def writeJournalRows(journalSerializedRow: JournalAkkaSerializationRow,
                                         tags: Set[String]
  ): Future[Unit] = {

    val journalInsert: DBIO[Long] = newJournalQueries.JournalTable
      .returning(newJournalQueries.JournalTable.map(_.ordering))
      .forceInsert(journalSerializedRow)

    val tagInserts: FixedSqlAction[Option[Int], NoStream, Effect.Write] = newJournalQueries.TagTable ++= tags
      .map(tag => TagRow(journalSerializedRow.ordering, tag))
      .toSeq

    journaldb.run(
      DBIO
        .seq(
          journalInsert,
          tagInserts
        )
        .withPinnedSession
        .transactionally
    )
  }
}
