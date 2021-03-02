package com.namely.chiefofstate.migration.versions.v1

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

import scala.concurrent.duration.Duration
import scala.concurrent.Await

object SchemasUtil {

  /**
   * legacyJournalStatement returns the journal ddl
   *
   * @return the sql statement
   */
  private val createJournalTableStmt: SqlAction[Int, NoStream, Effect] = {
    sqlu"""
     CREATE TABLE IF NOT EXISTS journal (
      ordering        BIGSERIAL,
      persistence_id  VARCHAR(255) NOT NULL,
      sequence_number BIGINT       NOT NULL,
      deleted         BOOLEAN      DEFAULT FALSE,
      tags            VARCHAR(255) DEFAULT NULL,
      message         BYTEA        NOT NULL,
      PRIMARY KEY (persistence_id, sequence_number)
     )"""
  }

  private val createJournalIndexStmt: SqlAction[Int, NoStream, Effect] = {
    sqlu"""CREATE UNIQUE INDEX IF NOT EXISTS journal_ordering_idx on journal(ordering)"""
  }

  /**
   * legacySnapshotTableStatement returns the legacy ddl statement
   *
   * @return the sql statement
   */
  private val createSnapshotTableStmt: SqlAction[Int, NoStream, Effect] =
    sqlu"""
     CREATE TABLE IF NOT EXISTS snapshot (
      persistence_id  VARCHAR(255) NOT NULL,
      sequence_number BIGINT       NOT NULL,
      created         BIGINT       NOT NULL,
      snapshot        BYTEA        NOT NULL,
      PRIMARY KEY (persistence_id, sequence_number)
     )"""

  private val createReadSideOffsetStmt: SqlAction[Int, NoStream, Effect] = {
    sqlu"""
           CREATE TABLE IF NOT EXISTS read_side_offsets (
              "PROJECTION_NAME" VARCHAR(255) NOT NULL,
              "PROJECTION_KEY" VARCHAR(255) NOT NULL,
              "CURRENT_OFFSET" VARCHAR(255) NOT NULL,
              "MANIFEST" VARCHAR(4) NOT NULL,
              "MERGEABLE" BOOLEAN NOT NULL,
              "LAST_UPDATED" BIGINT NOT NULL,
              PRIMARY KEY("PROJECTION_NAME", "PROJECTION_KEY")
          )
          """
  }

  private val createReadSideOffsetTableIndexStmt: SqlAction[Int, NoStream, Effect] = {
    sqlu"""
             CREATE INDEX IF NOT EXISTS "PROJECTION_NAME_INDEX" ON read_side_offsets ("PROJECTION_NAME")
            """
  }

  /**
   * creates the read side offset store table
   */
  def createReadSideOffsetTable(projectionJdbcConfig: DatabaseConfig[JdbcProfile]): Unit = {
    val stmt = DBIO
      .seq(
        createJournalTableStmt,
        createJournalIndexStmt,
        createReadSideOffsetStmt,
        createReadSideOffsetTableIndexStmt
      )
      .withPinnedSession
      .transactionally

    Await.result(projectionJdbcConfig.db.run(stmt), Duration.Inf)
  }
}
