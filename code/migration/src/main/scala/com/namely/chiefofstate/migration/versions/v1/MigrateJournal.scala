/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v1

import akka.actor.typed.ActorSystem
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.persistence.jdbc.journal.dao.legacy.ByteArrayJournalSerializer
import akka.persistence.jdbc.query.dao.legacy.ReadJournalQueries
import akka.stream.scaladsl.{Sink, Source}
import akka.NotUsed
import akka.persistence.journal.Tagged
import slick.jdbc.PostgresProfile.api._
import slickProfile.api._

import scala.concurrent.Future
import scala.util.Try

case class MigrateJournal(system: ActorSystem[_]) extends Migrate {
  private val queries: ReadJournalQueries = new ReadJournalQueries(profile, readJournalConfig)
  private val serializer: ByteArrayJournalSerializer =
    new ByteArrayJournalSerializer(serialization, readJournalConfig.pluginConfig.tagSeparator)

  /**
   * Fetch all events from the journal and deserialize them into PersistenceRepr.
   * <p>
   * Since the raw event payload is persisted as well as the tags in separate columns to be able
   * to use the new DAO we need to wrap the PersistenceRepr payload and the unpacked tags into
   * Tagged message. That way the new DAO will be able to properly unpack the PersistenceRepr and insert
   * it into the appropriate tables.
   * </p>
   */
  private def events: Source[PersistentRepr, NotUsed] = {
    Source
      .fromPublisher(
        journaldb.stream(queries.JournalTable.sortBy(_.sequenceNumber).result)
      )
      .via(serializer.deserializeFlow)
      .mapAsync(1)((reprAndOrdNr: Try[(PersistentRepr, Set[String], Long)]) => Future.fromTry(reprAndOrdNr))
      .map { case (repr, tags, _) =>
        repr.withPayload(Tagged(repr.payload, tags))
      }
  }

  /**
   * write all legacy events into the new journal tables applying the proper serialization
   */
  def run(): Unit = {
    events
      .mapAsync(1)((repr: PersistentRepr) => {
        defaultJournalDao.asyncWriteMessages(Seq(AtomicWrite(Seq(repr))))
      })
      .runWith(Sink.seq) // FIXME for performance
      .map(_ => ())
  }
}
