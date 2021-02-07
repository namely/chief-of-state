/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.legacy
import akka.actor.ActorSystem
import akka.persistence.{AtomicWrite, Persistence, PersistentRepr}
import akka.persistence.journal.EventAdapter
import akka.stream.scaladsl.Source
import akka.NotUsed
import com.typesafe.config.Config

import scala.collection.immutable
import scala.concurrent.Future
import scala.util.Try

/**
 * migrates the legacy journal data onto the new journal schema.
 * This is only used prior to COS version 0.8.0
 *
 * @param config the application config
 * @param system the actor system
 */
case class MigrateJournal(config: Config)(implicit system: ActorSystem) extends Migrate(config) {

  private val eventAdapters = Persistence(system).adaptersFor("", config)

  private def adaptEvents(repr: PersistentRepr): Seq[PersistentRepr] = {
    val adapter: EventAdapter = eventAdapters.get(repr.payload.getClass)
    adapter.fromJournal(repr.payload, repr.manifest).events.map(repr.withPayload)
  }

  /**
   * reads all the current events in the legacy journal
   *
   * @return the source of all the events
   */
  private def allEvents(): Source[PersistentRepr, NotUsed] = {
    legacyReadJournalDao
      .allPersistenceIdsSource(Long.MaxValue)
      .flatMapConcat((persistenceId: String) => {
        legacyReadJournalDao
          .messagesWithBatch(persistenceId, 0L, Long.MaxValue, readJournalConfig.maxBufferSize, None)
          .mapAsync(1)(reprAndOrdNr => Future.fromTry(reprAndOrdNr))
          .mapConcat { case (repr, _) =>
            adaptEvents(repr)
          }
      })
  }

  /**
   * write all legacy events into the new journal tables applying the proper serialization
   */
  def migrate(): Source[Seq[Try[Unit]], NotUsed] = {
    allEvents().mapAsync(1) { pr =>
      defaultJournalDao.asyncWriteMessages(immutable.Seq(AtomicWrite(pr)))
    }
  }
}
