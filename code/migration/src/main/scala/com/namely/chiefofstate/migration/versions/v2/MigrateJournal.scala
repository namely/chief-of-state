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
import akka.persistence.jdbc.journal.dao.{AkkaSerialization, JournalQueries}
import akka.persistence.jdbc.journal.dao.legacy.ByteArrayJournalSerializer
import akka.persistence.jdbc.journal.dao.JournalTables.{JournalAkkaSerializationRow, TagRow}
import akka.persistence.jdbc.query.dao.legacy.ReadJournalQueries
import akka.persistence.journal.Tagged
import akka.serialization.Serialization
import akka.stream.scaladsl.{Sink, Source}
import slick.dbio.Effect
import slick.jdbc.{JdbcBackend, JdbcProfile}
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

  private val defaultSchemaName = "cos"

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

  /**
   * write all legacy events into the new journal tables applying the proper serialization
   */
  def run(): Unit = {
    val future: Future[Done] = Source
      .fromPublisher(journaldb.stream(queries.JournalTable.sortBy(_.ordering).result))
      .via(serializer.deserializeFlow)
      .mapAsync(1)(reprAndOrdNr => Future.fromTry(reprAndOrdNr))
      .map { case (repr, tags, ordering) => repr.withPayload(Tagged(repr, tags)) -> ordering }
      .mapAsync(1)(tuple => {
        val (repr, ordering) = tuple
        // serializing the PersistentRepr using the same ordering received from the old journal
        val (row, tags): (JournalAkkaSerializationRow, Set[String]) = serialize(repr, ordering)

        // persist the data
        writeJournalRows(row, tags)
      })
      .runWith(Sink.ignore)

    Await.result(future, Duration.Inf)
  }

  /**
   * returns the next ordering value
   */
  private def nextOrderingValue(): Long = {
    val schemaName = journalConfig.journalTableConfiguration.schemaName.getOrElse(defaultSchemaName)
    val sequenceName = s"$schemaName.journal_ordering_seq"
    val stmt: DBIO[Long] =
      sql"""
             SELECT pg_catalog.nextval($sequenceName);
        """.as[Long].head

    Await.result(journaldb.run(stmt), Duration.Inf)
  }

  /**
   *  sets the next ordering value
   */
  def setNextOrderingValue(): Long = {
    val schemaName = journalConfig.eventJournalTableConfiguration.schemaName.getOrElse(defaultSchemaName)
    val sequenceName = s"$schemaName.event_journal_ordering_seq"
    val nextVal = nextOrderingValue()
    val stmt: DBIO[Long] =
      sql"""
             SELECT pg_catalog.setval($sequenceName, $nextVal, true);
        """.as[Long].head

    Await.result(journaldb.run(stmt), Duration.Inf)
  }

  /**
   *  serialize the PersistentRepr and construct a JournalAkkaSerializationRow and set of matching tags
   *
   * @param pr the PersistentRepr
   * @param ordering the ordering of the PersistentRepr
   * @return the tuple of JournalAkkaSerializationRow and set of tags
   */
  private def serialize(pr: PersistentRepr, ordering: Long): (JournalAkkaSerializationRow, Set[String]) = {

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
  private def writeJournalRows(journalSerializedRow: JournalAkkaSerializationRow, tags: Set[String]): Future[Unit] = {

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
