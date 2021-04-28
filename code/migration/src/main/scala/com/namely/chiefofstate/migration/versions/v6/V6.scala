/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v6
import com.namely.chiefofstate.migration.{ SchemasUtil, Version }
import org.slf4j.{ Logger, LoggerFactory }
import slick.basic.DatabaseConfig
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

case class V6(journalJdbcConfig: DatabaseConfig[JdbcProfile]) extends Version {
  final val log: Logger = LoggerFactory.getLogger(getClass)
  override def versionNumber: Int = 6

  /**
   * implement this method to upgrade the application to this version. This is
   * run in the same db transaction that commits the version number to the
   * database.
   *
   * @return a DBIO that runs this upgrade
   */
  override def upgrade(): DBIO[Unit] = DBIO.successful {}

  /**
   * creates the latest COS schema if no prior versions found.
   *
   * @return a DBIO that creates the version snapshot
   */
  override def snapshot(): DBIO[Unit] = {
    log.info(s"running snapshot for version #$versionNumber")
    SchemasUtil.createStoreTables(journalJdbcConfig)
    DBIO.successful {}
  }
}
