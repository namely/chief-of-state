/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v2

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object SchemasUtil {

  /**
   * event_journal DDL statement
   */
  private[versions] val createEventJournalStmt: SqlAction[Int, NoStream, Effect] = {
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
  private[versions] val createEventJournalIndexStmt: SqlAction[Int, NoStream, Effect] = {
    sqlu"""CREATE UNIQUE INDEX IF NOT EXISTS event_journal_ordering_idx ON event_journal(ordering)"""
  }

  /**
   * event_tag DDL statement
   */
  private[versions] val createEventTagStmt: SqlAction[Int, NoStream, Effect] = {
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
  private[versions] val createSnapshotStmt: SqlAction[Int, NoStream, Effect] = {
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

  /**
   * creates the read side offsets table
   *
   * @param tableName optional param to set the table name (used in v1 migration)
   * @return the DBIOAction creating the table and offset
   */
  private[versions] def createReadSideOffsetsStmt(
    tableName: String = "read_side_offsets"
  ): DBIOAction[Unit, NoStream, Effect] = {

    val table = sqlu"""
      CREATE TABLE IF NOT EXISTS #$tableName (
        projection_name VARCHAR(255) NOT NULL,
        projection_key VARCHAR(255) NOT NULL,
        current_offset VARCHAR(255) NOT NULL,
        manifest VARCHAR(4) NOT NULL,
        mergeable BOOLEAN NOT NULL,
        last_updated BIGINT NOT NULL,
        PRIMARY KEY(projection_name, projection_key)
      )
    """

    val ix = sqlu"""
    CREATE INDEX IF NOT EXISTS projection_name_index ON #$tableName (projection_name);
    """

    DBIO.seq(table, ix)
  }

  /**
   * creates the various write-side stores and read-side offset stores
   */
  def createJournalTables(journalJdbcConfig: DatabaseConfig[JdbcProfile]): Unit = {
    val ddlSeq = DBIO
      .seq(
        createEventJournalStmt,
        createEventJournalIndexStmt,
        createEventTagStmt,
        createSnapshotStmt,
        createReadSideOffsetsStmt()
      )
      .withPinnedSession
      .transactionally

    Await.result(journalJdbcConfig.db.run(ddlSeq), Duration.Inf)
  }

  /**
   * drops the legacy journal and snapshot tables
   *
   * @param journalJdbcConfig the database config
   */
  val dropLegacyJournalTablesStmt: SqlAction[Int, NoStream, Effect] = {
    sqlu"""
             DROP TABLE IF EXISTS
                journal,
                snapshot
             CASCADE
            """
  }

  /**
   * drops the journal and snapshot tables
   */
  def dropJournalTables(journalJdbcConfig: DatabaseConfig[JdbcProfile]): Unit = {
    val stmt = DBIO
      .seq(
        sqlu"""
             DROP TABLE IF EXISTS
                event_journal,
                event_tag,
                state_snapshot
             CASCADE
            """,
        sqlu"""
        DROP INDEX IF EXISTS event_journal_ordering_idx;
      """
      )
      .withPinnedSession
      .transactionally

    Await.result(journalJdbcConfig.db.run(stmt), Duration.Inf)
  }

  /**
   * SQL statement that updates the read_side_offsets current_offset
   * values to the event ordering on the new journal. This is necessary
   * as the ordering value is computed on insert with a postgres sequence,
   * so it's possible for the values to have changed, etc. This should be run
   * after populating the new journal but before dropping the old journal.
   */
  private[v2] val updateReadOffsets: SqlAction[Int, NoStream, Effect] = {
    sqlu"""
        with new_values(projection_key, projection_name, new_offset) as (
          select
            r.projection_key,
            r.projection_name,
            new_journal.ordering::varchar(255)
          from read_side_offsets r
          inner join journal old_journal
            on old_journal.ordering::varchar(255) = r.current_offset
          inner join event_journal new_journal
            on old_journal.persistence_id = new_journal.persistence_id
            and old_journal.sequence_number = new_journal.sequence_number
        )
        update read_side_offsets r
        set current_offset = new_values.new_offset
        from new_values
        where r.projection_key = new_values.projection_key
          and r.projection_name = new_values.projection_name
    """
  }
}
