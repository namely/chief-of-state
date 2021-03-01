package com.namely.chiefofstate.migration.versions.v1

import akka.actor.typed.ActorSystem
import com.namely.chiefofstate.migration.Version
import slick.basic.DatabaseConfig
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration.Duration
import scala.util.Try

case class V1__Version(journalJdbcConfig: DatabaseConfig[JdbcProfile], system: ActorSystem[_]) extends Version {
  implicit private val ec: ExecutionContextExecutor = system.executionContext

  override def versionNumber: Int = 1

  /**
   * implement this method to upgrade the application to this version. This is
   * run in the same db transaction that commits the version number to the
   * database.
   *
   * @return a DBIO that runs this upgrade
   */
  override def upgrade(): DBIO[Unit] = {
    val journalMigrator: MigrateJournal = MigrateJournal(system)
    val snapshotMigrator: MigrateSnapshot = MigrateSnapshot(system)
    val future: Future[Unit] = for {
      _ <- Future(journalMigrator.run())
      _ <- snapshotMigrator.run()
    } yield ()

    DBIO.successful(Await.result(future, Duration.Inf))
  }

  /**
   * implement this method to snapshot this version (run if no prior versions found)
   *
   * @return a DBIO that creates the version snapshot
   */
  override def snapshot(): DBIO[Unit] = DBIO.successful {}

  override def beforeUpgrade(): Try[Unit] = {
    Try(SchemasUtil.createIfExists(journalJdbcConfig))
  }
}
