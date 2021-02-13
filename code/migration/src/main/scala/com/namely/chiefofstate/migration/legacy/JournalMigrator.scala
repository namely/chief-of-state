/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.legacy
import akka.actor.ActorSystem
import akka.persistence.{AtomicWrite, Persistence, PersistentRepr}
import akka.persistence.journal.EventAdapter
import akka.stream.scaladsl.{Sink, Source}
import akka.NotUsed
import akka.persistence.jdbc.journal.dao.legacy.ByteArrayJournalSerializer
import akka.persistence.jdbc.journal.JdbcAsyncWriteJournal
import akka.persistence.jdbc.query.dao.legacy.ReadJournalQueries
import com.typesafe.config.Config

import scala.concurrent.Future

/**
 * migrates the legacy journal data onto the new journal schema.
 * This is only used prior to COS version 0.8.0
 *
 * @param config the application config
 * @param system the actor system
 */
final case class JournalMigrator(config: Config)(implicit system: ActorSystem) extends Migrator(config) {
  import profile.api._
  import system.dispatcher

  private val eventAdapters = Persistence(system).adaptersFor("", config)

  private def adaptEvents(repr: PersistentRepr): Seq[PersistentRepr] = {
    val adapter: EventAdapter = eventAdapters.get(repr.payload.getClass)
    adapter.fromJournal(repr.payload, repr.manifest).events.map(repr.withPayload)
  }

  private val queries: ReadJournalQueries = new ReadJournalQueries(profile, readJournalConfig)
  private val serializer: ByteArrayJournalSerializer =
    new ByteArrayJournalSerializer(serialization, readJournalConfig.pluginConfig.tagSeparator)

  private val journalAsyncWrite: JdbcAsyncWriteJournal = new JdbcAsyncWriteJournal(config)

  /**
   * reads all the current events in the legacy journal
   *
   * @return the source of all the events
   */
  private def allEvents(): Source[Seq[PersistentRepr], NotUsed] = {
    Source
      .fromPublisher(
        journaldb.stream(queries.JournalTable.sortBy(_.sequenceNumber).result)
      )
      .via(serializer.deserializeFlow)
      .mapAsync(1)(reprAndOrdNr => Future.fromTry(reprAndOrdNr))
      .map { case (repr, _, _) =>
        adaptEvents(repr)
      }
  }

  /**
   * write all legacy events into the new journal tables applying the proper serialization
   */
  def migrate(): Future[Unit] = {
    allEvents()
      .mapAsync(1)(list => journalAsyncWrite.asyncWriteMessages(Seq(AtomicWrite(list))))
      .limit(Long.MaxValue)
      .runWith(Sink.seq)
      .map(_ => ())
  }
}
