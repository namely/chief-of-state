/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
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

/**
 * Custom akka persistence snapshot store for restoring from the akka jdbc
 * journal directly. This eliminates the need for a separate snapshot table.
 *
 * @param config typesafe config for the snapshot
 */
class CosSnapshotStore(config: Config) extends SnapshotStore {

  implicit val ec: ExecutionContext = context.dispatcher
  implicit val system: ActorSystem = context.system

  private[chiefofstate] val readJournal: JdbcReadJournal = PersistenceQuery(system)
    .readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

  /**
   * implements loadAsync for akka persistence snapshots to read from the
   * jdbc journal and return an empty "snapshot" pointing to the highest
   * persisted sequence number. This works since COS persists each resulting
   * state in the journal.
   *
   * @param persistenceId persistence ID to restore
   * @param criteria snapshot criteria to restore
   * @return optional selected snapshot
   */
  override def loadAsync(persistenceId: String,
                         criteria: SnapshotSelectionCriteria
  ): Future[Option[SelectedSnapshot]] = {
    logger.debug(s"loadAsync, persistenceId=$persistenceId, criteria=$criteria")

    // get the events for this entity
    val source: Source[EventEnvelope, NotUsed] = readJournal
      .currentEventsByPersistenceId(persistenceId, criteria.minSequenceNr, criteria.maxSequenceNr)

    val maxEnvelopeFuture: Future[Option[EventEnvelope]] = source
      // fold to the max sequence number
      .runFold[Option[EventEnvelope]](None)((maxEnvelope: Option[EventEnvelope], nextEnvelope: EventEnvelope) => {
        maxEnvelope.map(prior => {
          if (prior.sequenceNr < nextEnvelope.sequenceNr) nextEnvelope else prior
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

  /**
   * dummy implementation of the async save of snapshots, which does nothing
   *
   * @param metadata ignored
   * @param snapshot ignored
   * @return successful future
   */
  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {
    Future.unit
  }

  /**
   * dummy deletion method, since there are no snapshots in the db
   *
   * @param metadata ignored
   * @return successful future
   */
  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = {
    Future.unit
  }

  /**
   * dummy implementation of deletion, since there are no snapshots in the db
   *
   * @param persistenceId ignored
   * @param criteria ignored
   * @return successful future
   */
  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = {
    Future.unit
  }

}

object CosSnapshotStore {
  private lazy val logger: Logger = LoggerFactory.getLogger(this.getClass().getName())
}
