/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.legacy

import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

case class ReadStoreMigrator(config: Config, readSideJdbcConfig: DatabaseConfig[PostgresProfile]) {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * rename the read_side_offsets table column names
   */
  private def columnsRenamingStatement() = {
    DBIO
      .seq(
        sqlu"""
        ALTER TABLE read_side_offsets RENAME COLUMN "PROJECTION_NAME" TO projection_name;
      """,
        sqlu"""
        ALTER TABLE read_side_offsets RENAME COLUMN "PROJECTION_KEY" TO projection_key;
      """,
        sqlu"""
        ALTER TABLE read_side_offsets RENAME COLUMN "CURRENT_OFFSET" TO current_offset;
      """,
        sqlu"""
        ALTER TABLE read_side_offsets RENAME COLUMN "MANIFEST" TO manifest;
      """,
        sqlu"""
        ALTER TABLE read_side_offsets RENAME COLUMN "MERGEABLE" TO mergeable;
      """,
        sqlu"""
        ALTER TABLE read_side_offsets RENAME COLUMN "LAST_UPDATED" TO last_updated;
      """,
        sqlu"""
        ALTER TABLE read_side_offsets DROP CONSTRAINT IF EXISTS "PK_PROJECTION_ID";
      """,
        sqlu"""
        DROP INDEX IF EXISTS "PROJECTION_NAME_INDEX";
      """,
        sqlu"""
        ALTER TABLE read_side_offsets ADD PRIMARY KEY (projection_name, projection_key);
      """,
        sqlu"""
         CREATE INDEX IF NOT EXISTS projection_name_index ON read_side_offsets (projection_name);
      """
      )
  }

  def renameColumns(): Future[Unit] = {
    readSideJdbcConfig.db.run(columnsRenamingStatement().withPinnedSession.transactionally)
  }
}
