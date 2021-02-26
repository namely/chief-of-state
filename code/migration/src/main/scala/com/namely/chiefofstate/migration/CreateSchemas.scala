/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration

import akka.actor.typed.ActorSystem
import akka.projection.slick.SlickProjection
import akka.Done
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.{JdbcProfile, PostgresProfile}
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

import scala.annotation.unused
import scala.concurrent.Future

@unused
case class CreateSchemas(journalJdbcConfig: DatabaseConfig[JdbcProfile],
                         projectionJdbcConfig: DatabaseConfig[PostgresProfile]
) {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  private def createEventJournal(): SqlAction[Int, NoStream, Effect] = {
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

  private def createEventJournalIndex(): SqlAction[Int, NoStream, Effect] = {
    sqlu"""CREATE UNIQUE INDEX IF NOT EXISTS event_journal_ordering_idx ON event_journal(ordering)"""
  }

  private def createEventTag(): SqlAction[Int, NoStream, Effect] = {
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
   * return the snapshot ddl statement
   * @return the sql statement
   */
  private def createSnapshot(): SqlAction[Int, NoStream, Effect] = {
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
   * returns the cos_migrations ddl statement
   *
   * @return
   */
  private def createCosMigrations(): SqlAction[Int, NoStream, Effect] = {
    sqlu"""
     CREATE TABLE IF NOT EXISTS cos_migrations (
      id BIGSERIAL PRIMARY KEY ,
      version VARCHAR(255) NOT NULL,
      description VARCHAR(255),
      is_successful BOOLEAN NOT NULL,
      cos_version VARCHAR(255) NOT NULL,
      created         BIGINT       NOT NULL
     )"""
  }

  private def createCosMigrationsIndex(): SqlAction[Int, NoStream, Effect] = {
    sqlu"""CREATE UNIQUE INDEX IF NOT EXISTS cos_migrations_version_idx ON cos_migrations(version)"""
  }

  /**
   * creates the various write-side stores and read-side offset stores
   */
  def journalStoresIfNotExists(): Future[Unit] = {
    val ddlSeq: DBIOAction[Unit, NoStream, _root_.slick.jdbc.PostgresProfile.api.Effect with Effect.Transactional] =
      DBIO
        .seq(
          createEventJournal(),
          createEventJournalIndex(),
          createEventTag(),
          createSnapshot(),
          createCosMigrations(),
          createCosMigrationsIndex()
        )
        .withPinnedSession
        .transactionally

    journalJdbcConfig.db.run(ddlSeq)
  }

  /**
   * creates the read side offset stores
   *
   * @param system the actor system
   */
  def readSideOffsetStoreIfNotExist()(implicit system: ActorSystem[_]): Future[Done] = {
    SlickProjection
      .createOffsetTableIfNotExists(projectionJdbcConfig)
  }
}
