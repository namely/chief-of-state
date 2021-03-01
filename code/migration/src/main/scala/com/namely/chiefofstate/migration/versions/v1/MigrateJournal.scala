/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v1

import akka.{Done, NotUsed}
import akka.actor.typed.ActorSystem
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.persistence.jdbc.journal.dao.legacy.ByteArrayJournalSerializer
import akka.persistence.jdbc.query.dao.legacy.ReadJournalQueries
import akka.persistence.journal.Tagged
import akka.stream.scaladsl.Source
import slick.jdbc.PostgresProfile.api._
import slickProfile.api._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.Try

case class MigrateJournal(system: ActorSystem[_]) extends Migrate {
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
