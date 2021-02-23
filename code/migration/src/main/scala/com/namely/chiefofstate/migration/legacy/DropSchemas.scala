/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.legacy

import akka.persistence.jdbc.config.{LegacyJournalTableConfiguration, LegacySnapshotTableConfiguration}
import com.namely.chiefofstate.migration.JdbcConfig
import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import java.sql.{Connection, Statement}

object DropSchemas {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Drops the legacy journal and snapshot tables
   *
   * @param config the application config
   * @return true when successful or an exception
   */
  def ifExists(config: Config): Boolean = {
    val legacyJournalConfig: LegacyJournalTableConfiguration = new LegacyJournalTableConfiguration(config)
    val legacySnapshotConfig: LegacySnapshotTableConfiguration = new LegacySnapshotTableConfiguration(config)
    val dbconfig: DatabaseConfig[JdbcProfile] = JdbcConfig.getWriteSideConfig(config)

    val sqlStatement: String =
      s"""
          DROP TABLE IF EXISTS 
              ${legacyJournalConfig.tableName},
              ${legacySnapshotConfig.tableName}
          CASCADE;
      """

    // let us get the database connection
    val conn: Connection = dbconfig.db.createSession().conn
    try {
      val stmt: Statement = conn.createStatement()
      try {
        log.info("droping chieofstate legacy journal and snapshot stores....")
        stmt.execute(sqlStatement)
      } finally {
        stmt.close()
      }
    } finally {
      log.info("chieofstate legacy journal and snapshot stores dropped. Releasing resources....")
      conn.close()
      dbconfig.db.close()
    }
  }
}
