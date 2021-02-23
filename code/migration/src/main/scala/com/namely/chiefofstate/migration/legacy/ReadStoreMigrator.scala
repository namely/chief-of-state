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
import slick.jdbc.PostgresProfile

import java.sql.{Connection, Statement}

object ReadStoreMigrator {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * rename the read_side_offsets table column names
   */
  private def columnsRenamingStatement(): Seq[String] = {
    Seq(
      s"""
        ALTER TABLE read_side_offsets RENAME COLUMN "PROJECTION_NAME" TO projection_name;
      """,
      s"""
        ALTER TABLE read_side_offsets RENAME COLUMN "PROJECTION_KEY" TO projection_key;
      """,
      s"""
        ALTER TABLE read_side_offsets RENAME COLUMN "CURRENT_OFFSET" TO current_offset;
      """,
      s"""
        ALTER TABLE read_side_offsets RENAME COLUMN "MANIFEST" TO manifest;
      """,
      s"""
        ALTER TABLE read_side_offsets RENAME COLUMN "MERGEABLE" TO mergeable;
      """,
      s"""
        ALTER TABLE read_side_offsets RENAME COLUMN "LAST_UPDATED" TO last_updated;
      """,
      s"""
        ALTER TABLE read_side_offsets DROP CONSTRAINT IF EXISTS "PK_PROJECTION_ID";
      """,
      s"""
        DROP INDEX IF EXISTS "PROJECTION_NAME_INDEX";
      """,
      s"""
        ALTER TABLE read_side_offsets ADD PRIMARY KEY (projection_name, projection_key);
      """,
      s"""
         CREATE INDEX IF NOT EXISTS projection_name_index ON read_side_offsets (projection_name);
      """
    )
  }

  def renameColumns(config: Config): Boolean = {
    val readSideJdbcConfig: DatabaseConfig[PostgresProfile] = JdbcConfig.getReadSideConfig(config)
    // let us get the database connection
    val conn: Connection = readSideJdbcConfig.db.createSession().conn

    try {
      val stmt: Statement = conn.createStatement()
      try {
        log.info("renaming chieofstate readSide Offset stores columns....")
        columnsRenamingStatement()
          .map(stmt.execute)
          .forall(identity)
      } finally {
        stmt.close()
      }
    } finally {
      log.info("chieofstate readSide Offset stores columns renamed. Releasing resources....")
      conn.close()
      readSideJdbcConfig.db.close()
    }
  }
}
