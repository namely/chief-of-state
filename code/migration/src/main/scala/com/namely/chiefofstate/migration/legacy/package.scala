/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration

import akka.actor.ActorSystem
import com.typesafe.config.Config

import scala.concurrent.{ExecutionContextExecutor, Future}

package object legacy {

  /**
   * migrate the legacy journal and snpashot data
   */
  def migrate(implicit system: ActorSystem): Future[Unit] = {
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    system.log.info("migration legacy journal and snapshot into the new journal and schemas")
    val config: Config = system.settings.config
    val journalMigrator: JournalMigrator = JournalMigrator(config)
    val snapshotMigrator: SnapshotMigrator = SnapshotMigrator(config)

    // let us rename the read-side-offset store columns
    ReadStoreMigrator.renameColumns(config)

    Future
      .sequence(
        List(Future {
               journalMigrator.migrate()
             },
             snapshotMigrator.migrate()
        )
      )
      .map(_ => system.log.info("legacy journal and snapshot data successfully migrated.. :)"))
  }

}
