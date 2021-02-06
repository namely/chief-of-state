package com.namely.chiefofstate.migration.legacy
import akka.actor.ActorSystem
import akka.persistence.{AtomicWrite, Persistence, PersistentRepr}
import akka.persistence.jdbc.config.{JournalConfig, ReadJournalConfig}
import akka.persistence.jdbc.db.SlickExtension
import akka.persistence.jdbc.journal.dao.DefaultJournalDao
import akka.persistence.jdbc.query.dao.legacy.ByteArrayReadJournalDao
import akka.persistence.journal.EventAdapter
import akka.serialization.SerializationExtension
import akka.stream.scaladsl.Source
import akka.NotUsed
import com.typesafe.config.Config
import slick.jdbc.JdbcBackend

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
  import system.dispatcher

  // get the various configuration
  private val journalConfig = new JournalConfig(config.getConfig("jdbc-journal"))
  private val readJournalConfig = new ReadJournalConfig(config.getConfig("jdbc-read-journal"))

  private val journaldb: JdbcBackend.Database =
    SlickExtension(system).database(config.getConfig("jdbc-read-journal")).database

  // get an instance of the legacy read journal dao
  private val legacyReadJournalDao: ByteArrayReadJournalDao =
    new ByteArrayReadJournalDao(journaldb, profile, readJournalConfig, SerializationExtension(system))

  // get an instance of the default journal dao
  private val defaultJournalDao: DefaultJournalDao =
    new DefaultJournalDao(journaldb, profile, journalConfig, SerializationExtension(system))

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
