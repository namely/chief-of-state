/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.legacy

import akka.actor.ActorSystem

import scala.concurrent.{ExecutionContextExecutor, Future}

case class JournalAndSnapshotMigration()(implicit system: ActorSystem) {
  def migrate(): Future[Unit] = {
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    system.log.info("migration legacy journal and snapshot into the new journal and schemas")
    val journalMigrator: JournalMigrator = JournalMigrator(system.settings.config)
    val snapshotMigrator: SnapshotMigrator = SnapshotMigrator(system.settings.config)

    Future
      .sequence(List(journalMigrator.migrateLegacyData(), snapshotMigrator.migrate()))
      .map(_ => system.log.info("legacy journal and snapshot successfully migrated.. :)"))
  }
}
