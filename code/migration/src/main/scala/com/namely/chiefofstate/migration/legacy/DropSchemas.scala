/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.legacy

import com.namely.chiefofstate.migration.JdbcConfig
import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

import scala.concurrent.Future

object DropSchemas {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Drops the legacy journal and snapshot tables
   *
   * @param config the application config
   * @return true when successful or an exception
   */
  def ifExists(config: Config): Future[Int] = {
    val legacyJournalTableName: String = config.getString("jdbc-journal.tables.legacy_journal.tableName")
    val legacySnapshotTableName: String = config.getString("jdbc-snapshot-store.tables.legacy_snapshot.tableName")
    val dbconfig: DatabaseConfig[JdbcProfile] = JdbcConfig.getWriteSideConfig(config)

    val sqlAction: SqlAction[Int, NoStream, Effect] =
      sqlu"""
          DROP TABLE IF EXISTS 
              $legacyJournalTableName,
              $legacySnapshotTableName
          CASCADE;
      """

    dbconfig.db.run(sqlAction.withPinnedSession.transactionally)
  }
}
