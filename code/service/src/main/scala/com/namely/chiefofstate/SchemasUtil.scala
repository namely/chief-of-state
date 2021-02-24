/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import akka.actor.typed.ActorSystem
import akka.projection.slick.SlickProjection
import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

import scala.concurrent.{ExecutionContextExecutor, Future}

/**
 * Utility class to create the necessary schemas for the write side
 */
object SchemasUtil {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * legacyJournalStatement returns the journal ddl
   *
   * @return the sql statement
   */
  private def createJournalTable(): SqlAction[Int, NoStream, Effect] = {
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

  private def createJournalIndex(): SqlAction[Int, NoStream, Effect] = {
    sqlu"""CREATE UNIQUE INDEX IF NOT EXISTS journal_ordering_idx on journal(ordering)"""
  }

  /**
   * legacySnapshotTableStatement returns the legacy ddl statement
   *
   * @return the sql statement
   */
  private def createSnapshotTable(): SqlAction[Int, NoStream, Effect] =
    sqlu"""
     CREATE TABLE IF NOT EXISTS snapshot (
      persistence_id  VARCHAR(255) NOT NULL,
      sequence_number BIGINT       NOT NULL,
      created         BIGINT       NOT NULL,
      snapshot        BYTEA        NOT NULL,
      PRIMARY KEY (persistence_id, sequence_number)
     )"""

  /**
   *  Attempts to create the various write side legacy data stores
   */
  private def createLegacySchemas(config: Config)(implicit system: ActorSystem[_]) = {
    val dbConfig: DatabaseConfig[PostgresProfile] =
      DatabaseConfig.forConfig[PostgresProfile]("write-side-slick", config)

    implicit val ec: ExecutionContextExecutor = system.executionContext

    val readSideJdbcConfig: DatabaseConfig[PostgresProfile] =
      DatabaseConfig.forConfig("akka.projection.slick", config)

    val dbioActions: DBIOAction[Unit, NoStream, Effect with Effect.Transactional] = DBIO
      .seq(
        createJournalTable(),
        createJournalIndex(),
        createSnapshotTable()
      )
      .withPinnedSession
      .transactionally

    for {
      _ <- dbConfig.db.run(dbioActions)
      _ <- SlickProjection
        .createOffsetTableIfNotExists(readSideJdbcConfig)
    } yield ()
  }

  /**
   * Creates the required schemas for the write side data stores
   *
   * @param config the application config
   * @return true when successful and false when it fails
   */
  def createIfNotExists(config: Config)(implicit system: ActorSystem[_]): Future[Unit] = createLegacySchemas(config)
}
