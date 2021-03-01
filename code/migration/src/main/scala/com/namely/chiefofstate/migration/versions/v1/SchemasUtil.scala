/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v1

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

import scala.concurrent.Future

object SchemasUtil {

  /**
   * event_journal DDL statement
   */
  private val createEventJournalStmt: SqlAction[Int, NoStream, Effect] = {
    sqlu"""
        CREATE TABLE IF NOT EXISTS event_journal(
          ordering BIGSERIAL,
          persistence_id VARCHAR(255) NOT NULL,
          sequence_number BIGINT NOT NULL,
          deleted BOOLEAN DEFAULT FALSE NOT NULL,

          writer VARCHAR(255) NOT NULL,
          write_timestamp BIGINT,
          adapter_manifest VARCHAR(255),

          event_ser_id INTEGER NOT NULL,
          event_ser_manifest VARCHAR(255) NOT NULL,
          event_payload BYTEA NOT NULL,

          meta_ser_id INTEGER,
          meta_ser_manifest VARCHAR(255),
          meta_payload BYTEA,

          PRIMARY KEY(persistence_id, sequence_number)
        )"""
  }

  /**
   * event_journal index Sql statement
   */
  private val createEventJournalIndexStmt: SqlAction[Int, NoStream, Effect] = {
    sqlu"""CREATE UNIQUE INDEX IF NOT EXISTS event_journal_ordering_idx ON event_journal(ordering)"""
  }

  /**
   * event_tag DDL statement
   */
  private val createEventTagStmt: SqlAction[Int, NoStream, Effect] = {
    sqlu"""
        CREATE TABLE IF NOT EXISTS event_tag(
            event_id BIGINT,
            tag VARCHAR(256),
            PRIMARY KEY(event_id, tag),
            CONSTRAINT fk_event_journal
              FOREIGN KEY(event_id)
              REFERENCES event_journal(ordering)
              ON DELETE CASCADE
        );
      """
  }

  /**
   * state_snapshot DDL statement
   */
  private val createSnapshotStmt: SqlAction[Int, NoStream, Effect] = {
    sqlu"""
     CREATE TABLE IF NOT EXISTS state_snapshot (
      persistence_id VARCHAR(255) NOT NULL,
      sequence_number BIGINT NOT NULL,
      created BIGINT NOT NULL,

      snapshot_ser_id INTEGER NOT NULL,
      snapshot_ser_manifest VARCHAR(255) NOT NULL,
      snapshot_payload BYTEA NOT NULL,

      meta_ser_id INTEGER,
      meta_ser_manifest VARCHAR(255),
      meta_payload BYTEA,

      PRIMARY KEY(persistence_id, sequence_number)
     )"""
  }

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
   * creates the various write-side stores and read-side offset stores
   */
  def createJournalTables(journalJdbcConfig: DatabaseConfig[JdbcProfile]): Future[Unit] = {
    val ddlSeq = DBIO
      .seq(
        createEventJournalStmt,
        createEventJournalIndexStmt,
        createEventTagStmt,
        createSnapshotStmt
      )
      .withPinnedSession
      .transactionally
    journalJdbcConfig.db.run(ddlSeq)
  }

  /**
   * creates the read side offset store table
   */
  def createReadSideOffsetTable(projectionJdbcConfig: DatabaseConfig[JdbcProfile]): Future[Unit] = {
    val ddlSeq = DBIO
      .seq(
        createReadSideOffsetStmt,
        createReadSideOffsetTableIndexStmt
      )
      .withPinnedSession
      .transactionally
    projectionJdbcConfig.db.run(ddlSeq)
  }

  /**
   * drops the legacy journal and snapshot tables
   *
   * @param journalJdbcConfig the database config
   */
  def dropLegacyJournalTables(journalJdbcConfig: DatabaseConfig[JdbcProfile]): Future[Int] = {
    journalJdbcConfig.db.run(
      sqlu"""
             DROP TABLE IF EXISTS 
                journal,
                snapshot
             CASCADE 
            """.withPinnedSession.transactionally
    )
  }
}
