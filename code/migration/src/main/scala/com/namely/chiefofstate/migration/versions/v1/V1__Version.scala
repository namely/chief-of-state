/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v1

import akka.actor.typed.ActorSystem
import com.namely.chiefofstate.migration.Version
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration.Duration
import scala.util.Try

case class V1__Version(journalJdbcConfig: DatabaseConfig[JdbcProfile],
                       projectionJdbcConfig: DatabaseConfig[JdbcProfile]
)(implicit system: ActorSystem[_])
    extends Version {
  implicit private val ec: ExecutionContextExecutor = system.executionContext

  final val log: Logger = LoggerFactory.getLogger(getClass)

  override def versionNumber: Int = 1

  /**
   * implement this method to upgrade the application to this version. This is
   * run in the same db transaction that commits the version number to the
   * database.
   *
   * @return a DBIO that runs this upgrade
   */
  override def upgrade(): DBIO[Unit] = {
    log.info(s"migrating ChiefOfState stores to $versionNumber")
    DBIO.successful {}
  }

  /**
   * implement this method to snapshot this version (run if no prior versions found)
   *
   * @return a DBIO that creates the version snapshot
   */
  override def snapshot(): DBIO[Unit] = DBIO.successful {}

  /**
   * performs the following actions:
   * <p>
   *   <ul>
   *     <li> create the new journal and snapshot tables
   *     <li> migrate the data from the old journal into the newly created journal table
   *     <li> migrate the data from the old snapshot into the newly crated snapshot table
   *     <li> create the read side offset store table
   *   </ul>
   * </p>
   *
   *  @return Success if the method succeeds
   */
  override def beforeUpgrade(): Try[Unit] = {
    val journalMigrator: MigrateJournal = MigrateJournal(system)
    val snapshotMigrator: MigrateSnapshot = MigrateSnapshot(system)
    val execution: Future[Unit] = for {
      _ <- SchemasUtil.createJournalTables(journalJdbcConfig)
      _ <- Future(journalMigrator.run())
      _ <- snapshotMigrator.run()
      _ <- SchemasUtil.createReadSideOffsetTable(projectionJdbcConfig)
    } yield ()

    Try(Await.result(execution, Duration.Inf))
  }

  /**
   * executed when migration done. It deletes the old journal and snapshot tables
   */
  override def afterUpgrade(): Try[Unit] = {
    Try(SchemasUtil.dropLegacyJournalTables(journalJdbcConfig))
  }
}
