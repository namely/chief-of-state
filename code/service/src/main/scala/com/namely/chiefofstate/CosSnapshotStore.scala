package com.namely.chiefofstate

import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SelectedSnapshot, SnapshotSelectionCriteria, SnapshotMetadata}
import scala.concurrent.Future
import org.slf4j.{Logger, LoggerFactory}
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.PersistenceQuery
import com.typesafe.config.Config
import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import CosSnapshotStore.logger
import akka.stream.scaladsl.Source
import akka.persistence.query.EventEnvelope
import akka.NotUsed
import com.namely.protobuf.chiefofstate.v1.persistence.StateWrapper

class CosSnapshotStore(config: Config) extends SnapshotStore {

  implicit val ec: ExecutionContext = context.dispatcher
  implicit val system: ActorSystem = context.system

  private[chiefofstate] val readJournal: JdbcReadJournal = PersistenceQuery(system)
    .readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    logger.debug(s"loadAsync, persistenceId=$persistenceId, criteria=$criteria")

    // get the events for this entity
    val source: Source[EventEnvelope, NotUsed] = readJournal
      .currentEventsByPersistenceId(persistenceId, Long.MinValue, Long.MaxValue)

    val maxEnvelopeFuture: Future[Option[EventEnvelope]] = source
    // fold to the max sequence number
    .runFold[Option[EventEnvelope]](None)((maxEnvelope: Option[EventEnvelope], nextEnvelope: EventEnvelope) => {
      maxEnvelope.map(prior => {
        if(prior.sequenceNr < nextEnvelope.sequenceNr) prior else nextEnvelope
      })
    })

    maxEnvelopeFuture
    .map(_.map((maxEnvelope: EventEnvelope) => {
      // FIXME, make a debug log
      logger.info(s"restoring, persistenceId=${maxEnvelope.persistenceId}, sequenceNo=${maxEnvelope.persistenceId}")

      SelectedSnapshot(
        metadata = SnapshotMetadata(
          persistenceId = maxEnvelope.persistenceId,
          sequenceNr = maxEnvelope.sequenceNr,
          timestamp = maxEnvelope.timestamp
        ),
        StateWrapper.defaultInstance
      )
    }))
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {
    Future.unit
  }

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = {
    Future.unit
  }

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = {
    Future.unit
  }

}

object CosSnapshotStore {
  private lazy val logger: Logger = LoggerFactory.getLogger(this.getClass().getName())
}
