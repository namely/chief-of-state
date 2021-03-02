/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v1

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object RenameColumns {

  /**
   * rename the read_side_offsets table column names
   */
  private def columnsRenamingStatement(tableName: String) = {
    DBIO
      .seq(
        sqlu"""
        ALTER TABLE #$tableName RENAME COLUMN "PROJECTION_NAME" TO projection_name;
      """,
        sqlu"""
        ALTER TABLE #$tableName RENAME COLUMN "PROJECTION_KEY" TO projection_key;
      """,
        sqlu"""
        ALTER TABLE #$tableName RENAME COLUMN "CURRENT_OFFSET" TO current_offset;
      """,
        sqlu"""
        ALTER TABLE #$tableName RENAME COLUMN "MANIFEST" TO manifest;
      """,
        sqlu"""
        ALTER TABLE #$tableName RENAME COLUMN "MERGEABLE" TO mergeable;
      """,
        sqlu"""
        ALTER TABLE #$tableName RENAME COLUMN "LAST_UPDATED" TO last_updated;
      """,
        sqlu"""
        ALTER TABLE #$tableName DROP CONSTRAINT IF EXISTS "PK_PROJECTION_ID";
      """,
        sqlu"""
        DROP INDEX IF EXISTS "PROJECTION_NAME_INDEX";
      """,
        sqlu"""
        ALTER TABLE #$tableName ADD PRIMARY KEY (projection_name, projection_key);
      """,
        sqlu"""
         CREATE INDEX IF NOT EXISTS projection_name_index ON #$tableName (projection_name);
      """
      )
  }

  /**
   * renames the read side offset store table name
   *
   * @param projectionJdbcConfig the projection jdbc configuration
   * @param tableName the table name
   */
  def rename(projectionJdbcConfig: DatabaseConfig[JdbcProfile], tableName: String): Unit = {
    Await.result(projectionJdbcConfig.db.run(columnsRenamingStatement(tableName).withPinnedSession.transactionally),
                 Duration.Inf
    )
  }

}
