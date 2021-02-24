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

    for {
      _ <- ReadStoreMigrator.renameColumns(config)
      _ <- Future(journalMigrator.migrate())
      _ <- snapshotMigrator.migrate()
    } yield ()
  }
}
