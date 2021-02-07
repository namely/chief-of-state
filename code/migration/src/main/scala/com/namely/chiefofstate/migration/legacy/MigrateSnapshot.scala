/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.legacy
import akka.actor.ActorSystem
import akka.persistence.SnapshotMetadata
import akka.stream.scaladsl.Source
import akka.NotUsed
import com.typesafe.config.Config

import scala.concurrent.Future

/**
 *  migrates the legacy snapshot data onto the new journal schema.
 *
 * @param config the application config
 * @param system the actor system
 */
class MigrateSnapshot(config: Config)(implicit system: ActorSystem) extends Migrate(config) {
  import system.dispatcher

  /**
   * write the latest state snapshot into the new snapshot table applying the proper serialization
   */
  def migrate(): Source[Option[Future[Unit]], NotUsed] = {
    legacyReadJournalDao
      .allPersistenceIdsSource(Long.MaxValue)
      .mapAsync(1) { persistenceId: String =>
        legacySnapshotDao
          .latestSnapshot(persistenceId)
          .map((o: Option[(SnapshotMetadata, Any)]) => {
            o.map(result => {
              val (meta, data) = result
              defaultSnapshotDao
                .save(meta, data)
            })
          })
      }
  }
}
