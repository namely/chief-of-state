/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.legacy

import akka.actor.ActorSystem
import com.typesafe.config.Config
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import scala.concurrent.{ExecutionContextExecutor, Future}

case class LegacyMigrator(config: Config, projectionJdbcConfig: DatabaseConfig[PostgresProfile])(implicit
  system: ActorSystem
) {
  implicit private val ec: ExecutionContextExecutor = system.dispatcher

  /**
   * executes the migration
   * @return
   */
  def run(): Future[Unit] = {
    val config: Config = system.settings.config
    val journalMigrator: JournalMigrator = JournalMigrator(config)
    val snapshotMigrator: SnapshotMigrator = SnapshotMigrator(config)

    for {
      _ <- Future(journalMigrator.migrate())
      _ <- snapshotMigrator.migrate()
    } yield ()
  }
}
