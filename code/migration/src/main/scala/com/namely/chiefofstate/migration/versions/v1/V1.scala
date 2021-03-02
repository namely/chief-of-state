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
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContextExecutor

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
  implicit val ec: ExecutionContextExecutor = system.executionContext
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
    V1.columnsRenamingStatement(tableName).flatMap(_ => DBIO.successful {})
  }

  /**
   * implement this method to snapshot this version (run if no prior versions found)
   *
   * @return a DBIO that creates the version snapshot
   */
  override def snapshot(): DBIO[Unit] = DBIO.failed(new RuntimeException("snaphotting not allowed in this version"))
}

object V1 {
  private[v1] def columnsRenamingStatement(tableName: String) = {
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
}
