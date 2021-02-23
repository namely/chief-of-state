/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.legacy
import akka.actor.ActorSystem
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.persistence.jdbc.journal.dao.legacy.ByteArrayJournalSerializer
import akka.persistence.jdbc.query.dao.legacy.ReadJournalQueries
import akka.persistence.journal.Tagged
import akka.stream.scaladsl.{Sink, Source}
import akka.NotUsed
import com.typesafe.config.Config
import slick.jdbc.PostgresProfile.api._
import slickProfile.api._

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Try

/**
 * migrates the legacy journal data onto the new journal schema.
 * This is only used prior to COS version 0.8.0
 *
 * @param config the application config
 * @param system the actor system
 */
final case class JournalMigrator(config: Config)(implicit system: ActorSystem) extends Migrator(config) {
  implicit private val ec: ExecutionContextExecutor = system.dispatcher

  private val queries: ReadJournalQueries = new ReadJournalQueries(profile, readJournalConfig)
  private val serializer: ByteArrayJournalSerializer =
    new ByteArrayJournalSerializer(serialization, readJournalConfig.pluginConfig.tagSeparator)

  private def events(): Source[PersistentRepr, NotUsed] = {
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
  def migrate(): Unit = {
    events()
      .mapAsync(1)((repr: PersistentRepr) => {
        defaultJournalDao.asyncWriteMessages(Seq(AtomicWrite(Seq(repr))))
      })
      .limit(Long.MaxValue)
      .runWith(Sink.seq) // FIXME for performance
      .map(_ => ())
  }

}
