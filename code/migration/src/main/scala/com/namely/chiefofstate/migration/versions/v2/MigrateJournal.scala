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
import akka.persistence.jdbc.journal.dao.legacy.JournalRow
import scala.util.Try
import akka.persistence.serialization.MessageFormats.PersistentMessage

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
      // transform to new types and extract tags
      .map(journalRow => {
        upgradeJournalRow(journalRow) match {
          case Success(newRow) => (newRow, upgradeTags(journalRow.tags))
          case Failure(e) => throw e
        }
      })
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

  /**
    * convert the old akka journal row to the new one, assuming proto serialization
    * was used
    *
    * @param row a legacy akka journal row with internal proto serialization
    * @return the new journal serialized row
    */
  private[versions] def upgradeJournalRow(row: JournalRow): Try[JournalAkkaSerializationRow] = {
    // parse the old proto representation from the old journal row
    Try(PersistentMessage.parseFrom(row.message))
    // convert to the new row
    .map(persistentMessage => {
      JournalAkkaSerializationRow(
        ordering = row.ordering,
        deleted = row.deleted,
        persistenceId = row.persistenceId,
        sequenceNumber = row.sequenceNumber,
        writer = persistentMessage.getWriterUuid(),
        writeTimestamp = persistentMessage.getTimestamp(),
        adapterManifest = persistentMessage.getMetadata().getPayloadManifest().toString("utf-8"),
        eventPayload = persistentMessage.getMetadata().getPayload().toByteArray(),
        eventSerId = persistentMessage.getMetadata().getSerializerId(),
        eventSerManifest = "", // TODO
        metaPayload = None, // TODO
        metaSerId = None, // TODO
        metaSerManifest = None // TODO
      )
    })
  }

  /**
    * converts the old tags serialization (csv) to the new one
    *
    * @param oldTags optional string repr of tags from old journal
    * @return set of string tags
    */
  private[versions] def upgradeTags(oldTags: Option[String]): Set[String] = {
    oldTags.map(_.split(",").toSet).getOrElse(Set.empty[String])
  }
}
