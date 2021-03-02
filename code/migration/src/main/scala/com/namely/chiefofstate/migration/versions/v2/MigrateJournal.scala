/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v2

import akka.{actor, Done, NotUsed}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.persistence.jdbc.config.{JournalConfig, ReadJournalConfig}
import akka.persistence.jdbc.db.SlickExtension
import akka.persistence.jdbc.journal.dao.legacy.ByteArrayJournalSerializer
import akka.persistence.jdbc.journal.dao.DefaultJournalDao
import akka.persistence.jdbc.query.dao.legacy.ReadJournalQueries
import akka.persistence.journal.Tagged
import akka.serialization.Serialization
import akka.stream.scaladsl.Source
import slick.jdbc.{JdbcBackend, JdbcProfile}
import slick.jdbc.PostgresProfile.api._
import slickProfile.api._

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration.Duration
import scala.util.Try

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

  // the journal, read journal and snapshot config
  private val journalConfig: JournalConfig = new JournalConfig(system.settings.config.getConfig("jdbc-journal"))
  private val readJournalConfig: ReadJournalConfig = new ReadJournalConfig(
    system.settings.config.getConfig("jdbc-read-journal")
  )
  // the various databases
  private val journaldb: JdbcBackend.Database =
    SlickExtension(system).database(system.settings.config.getConfig("jdbc-read-journal")).database

  // get an instance of the default journal dao
  private val defaultJournalDao: DefaultJournalDao =
    new DefaultJournalDao(journaldb, profile, journalConfig, serialization)

  private val queries: ReadJournalQueries = new ReadJournalQueries(profile, readJournalConfig)
  private val serializer: ByteArrayJournalSerializer =
    new ByteArrayJournalSerializer(serialization, readJournalConfig.pluginConfig.tagSeparator)

  /**
   * write all legacy events into the new journal tables applying the proper serialization
   */
  def run(): Unit = {
    val source: Source[Try[(PersistentRepr, Set[String], Long)], NotUsed] = Source
      .fromPublisher(
        journaldb.stream(queries.JournalTable.result)
      )
      .via(serializer.deserializeFlow)

    val transformer: Source[PersistentRepr, NotUsed] = source
      .mapAsync(1)((reprAndOrdNr: Try[(PersistentRepr, Set[String], Long)]) => Future.fromTry(reprAndOrdNr))
      .map { case (repr, tags, _) =>
        repr.withPayload(Tagged(repr.payload, tags))
      }

    val loader: Future[Done] = transformer
      .mapAsync(1)((repr: PersistentRepr) => {
        defaultJournalDao.asyncWriteMessages(Seq(AtomicWrite(Seq(repr))))
      })
      .run()

    Await.result(loader, Duration.Inf)
  }
}
