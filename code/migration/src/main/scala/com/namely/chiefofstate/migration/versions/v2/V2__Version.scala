/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v2

import akka.actor.typed.ActorSystem
import akka.serialization.{Serialization, SerializationExtension}
import com.namely.chiefofstate.migration.Version
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Success, Try}

/**
 * V2 migration
 *
 * @param journalJdbcConfig the journal configuration
 * @param projectionJdbcConfig the projection configuration
 * @param system the actor system
 */
case class V2__Version(journalJdbcConfig: DatabaseConfig[JdbcProfile],
                       projectionJdbcConfig: DatabaseConfig[JdbcProfile]
)(implicit system: ActorSystem[_])
    extends Version {
  implicit private val ec: ExecutionContextExecutor = system.executionContext

  final val log: Logger = LoggerFactory.getLogger(getClass)

  override def versionNumber: Int = 2

  /**
   * implement this method to upgrade the application to this version. This is
   * run in the same db transaction that commits the version number to the
   * database.
   *
   * @return a DBIO that runs this upgrade
   */
  override def upgrade(): DBIO[Unit] = {
    log.info(s"finalizing ChiefOfState $versionNumber migration")
    SchemasUtil.dropLegacyJournalTables(journalJdbcConfig).flatMap(_ => DBIO.successful {})
  }

  /**
   * implement this method to snapshot this version (run if no prior versions found)
   *
   * @return a DBIO that creates the version snapshot
   */
  override def snapshot(): DBIO[Unit] = DBIO.failed(new RuntimeException("snaphotting not allowed in this version"))

  /**
   * performs the following actions:
   * <p>
   *   <ul>
   *     <li> attempt to drop the new journal and snapshot tables in case a previous migration failed
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
    val serialization: Serialization = SerializationExtension(system)
    val profile: JdbcProfile = journalJdbcConfig.profile
    val journalMigrator: MigrateJournal = MigrateJournal(system, profile, serialization)
    val snapshotMigrator: MigrateSnapshot = MigrateSnapshot(system, profile, serialization)

    Try {
      log.info("creating new ChiefOfState journal tables")
      SchemasUtil.createJournalTables(journalJdbcConfig)

      log.info("migrating ChiefOfState old journal data to the new journal table")
      journalMigrator.run()

      log.info("migrating ChiefOfState old snapshot data onto the new snapshot table")
      snapshotMigrator.run()
    }
  }

  /**
   * executed when migration done. It deletes the old journal and snapshot tables
   */
  override def afterUpgrade(): Try[Unit] = Success {}
}
