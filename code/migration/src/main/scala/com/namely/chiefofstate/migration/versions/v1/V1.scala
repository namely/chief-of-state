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

/**
 * V1 migration only rename the read side offset store table columns name from
 * uppercase to lower case
 *
 * @param projectionJdbcConfig the projection configuration
 * @param system the actor system
 */
case class V1(
  projectionJdbcConfig: DatabaseConfig[JdbcProfile]
)(implicit system: ActorSystem[_])
    extends Version {
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
    log.info(s"finalizing ChiefOfState migration: #$versionNumber")
    val tableName: String = system.settings.config.getString("akka.projection.slick.offset-store.table")
    DBIO.successful(RenameColumns.rename(projectionJdbcConfig, tableName))
  }

  /**
   * implement this method to snapshot this version (run if no prior versions found)
   *
   * @return a DBIO that creates the version snapshot
   */
  override def snapshot(): DBIO[Unit] = DBIO.failed(new RuntimeException("snaphotting not allowed in this version"))
}
