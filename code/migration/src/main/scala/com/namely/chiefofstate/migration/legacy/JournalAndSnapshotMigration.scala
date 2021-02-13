package com.namely.chiefofstate.migration.legacy

import akka.actor.ActorSystem
import com.namely.chiefofstate.migration.DataMigration

import scala.concurrent.{ExecutionContextExecutor, Future}

case class JournalAndSnapshotMigration(version: String)(implicit system: ActorSystem) extends DataMigration {
  override def migrate(): Future[Unit] = {
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    system.log.info("migration legacy journal and snapshot into the new journal and schemas")
    val journalMigrator: JournalMigrator = JournalMigrator(system.settings.config)
    val snapshotMigrator: SnapshotMigrator = SnapshotMigrator(system.settings.config)

    Future
      .sequence(List(journalMigrator.migrate(), snapshotMigrator.migrate()))
      .map(_ => ())
  }
}
