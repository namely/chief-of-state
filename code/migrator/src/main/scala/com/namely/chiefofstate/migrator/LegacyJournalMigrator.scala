package com.namely.chiefofstate.migrator

import akka.actor.ActorSystem
import akka.NotUsed
import akka.persistence.{AtomicWrite, Persistence, PersistentRepr}
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.Config

import scala.collection.immutable
import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.util.Try

final case class LegacyJournalMigrator(config: Config)(implicit system: ActorSystem) {
  import system.dispatcher

  // get the write plugin ID. This will help apply events adapters when reading the journal
  val writePluginId: String = config.getString("write-plugin")

  private val migratorConfig = MigratorConfig(config)
  private val daos = MigratorDaos(migratorConfig)

  private val eventAdapters = Persistence(system).adaptersFor(writePluginId, config)

  private def adaptEvents(repr: PersistentRepr): Seq[PersistentRepr] = {
    val adapter = eventAdapters.get(repr.payload.getClass)
    adapter.fromJournal(repr.payload, repr.manifest).events.map(repr.withPayload)
  }

  /**
   * reads all the current events in the legacy journal
   *
   * @return the source of all the events
   */
  def readEvents(): Source[PersistentRepr, NotUsed] = {
    daos.legacyReadJournalDao
      .allPersistenceIdsSource(Long.MaxValue)
      .flatMapConcat((persistenceId: String) => {
        daos.legacyReadJournalDao
          .messagesWithBatch(persistenceId, 0L, Long.MaxValue, migratorConfig.readJournalConfig.maxBufferSize, None)
          .mapAsync(1)(reprAndOrdNr => Future.fromTry(reprAndOrdNr))
          .mapConcat { case (repr, _) =>
            adaptEvents(repr)
          }
      })
  }

  /**
   * write all legacy events into the new journal tables applying the proper serialization
   */
  def writeEvents(): Future[Future[Seq[Try[Unit]]]] = {
    readEvents()
      .runWith(Sink.seq) // TODO: find the best way to load all the events in memory
      .map((sq: Seq[PersistentRepr]) => {
        daos.defaultJournalDao.asyncWriteMessages(
          immutable.Seq(
            AtomicWrite(sq)
          )
        )
      })
  }
}
