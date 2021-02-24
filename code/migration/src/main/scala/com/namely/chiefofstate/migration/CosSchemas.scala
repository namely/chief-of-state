/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration

import akka.actor.typed.ActorSystem
import akka.projection.slick.SlickProjection
import com.github.ghik.silencer.silent
import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.{JdbcProfile, PostgresProfile}
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

@silent
object CosSchemas {
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
    sqlu"""CREATE UNIQUE INDEX event_journal_ordering_idx ON event_journal(ordering)"""
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

  private def createCosVersion(): SqlAction[Int, NoStream, Effect] = {
    sqlu"""
     CREATE TABLE IF NOT EXISTS cos_versions (
      id BIGSERIAL PRIMARY KEY ,
      version VARCHAR(255) NOT NULL,
      data_migration_version VARCHAR(255) NOT NULL
     )"""
  }

  private def createCosVersionIndex(): SqlAction[Int, NoStream, Effect] = {
    sqlu"""CREATE UNIQUE INDEX cos_versions_version_idx ON cos_versions(version)"""
  }

  /**
   * creates the various write-side stores and read-side offset stores
   *
   * @param config the application config
   */
  def createIfNotExists(config: Config)(implicit system: ActorSystem[_]): Future[Unit] = {

    implicit val ec: ExecutionContextExecutor = system.executionContext

    val dbconfig: DatabaseConfig[JdbcProfile] = JdbcConfig.getWriteSideConfig(config)
    val readSideJdbcConfig: DatabaseConfig[PostgresProfile] = JdbcConfig.getReadSideConfig(config)

    val ddlSeq: DBIOAction[Unit, NoStream, _root_.slick.jdbc.PostgresProfile.api.Effect with Effect.Transactional] =
      DBIO
        .seq(
          createEventJournal(),
          createEventJournalIndex(),
          createEventTag(),
          createSnapshot(),
          createCosVersion(),
          createCosVersionIndex()
        )
        .withPinnedSession
        .transactionally

    for {
      _ <- dbconfig.db.run(ddlSeq)
      _ <- SlickProjection
        .createOffsetTableIfNotExists(readSideJdbcConfig)
    } yield ()
  }

  /**
   * checks the existence of the journal and snapshot tables in the given schema.
   *
   * @param config the application config
   * @param ec the scala execution context
   */
  def checkIfLegacyTablesExist(
    config: Config
  )(implicit ec: ExecutionContext): Future[(Vector[String], Vector[String])] = {
    val legacyJournalTableName: String = config.getString("jdbc-journal.tables.legacy_journal.tableName")
    val legacySnapshotTableName: String = config.getString("jdbc-snapshot-store.tables.legacy_snapshot.tableName")
    val legacyJournalSchemaName: String = config.getString("jdbc-journal.tables.legacy_journal.schemaName")
    val legacySnapshotSchemaName: String = config.getString("jdbc-snapshot-store.tables.legacy_snapshot.schemaName")

    val dbconfig: DatabaseConfig[JdbcProfile] = JdbcConfig.getWriteSideConfig(config)
    val legacyJournalTableNameLookupSql: String = s"$legacyJournalSchemaName.$legacyJournalTableName"
    val legacySnapshotTableNameLookupSql: String = s"$legacySnapshotSchemaName.$legacySnapshotTableName"

    for {
      jname <- dbconfig.db.run(
        sql"""SELECT to_regclass($legacyJournalTableNameLookupSql)""".as[String]
      )
      sname <- dbconfig.db.run(
        sql"""SELECT to_regclass($legacySnapshotTableNameLookupSql)""".as[String]
      )
    } yield (jname, sname)
  }

  /**
   * checks the cos_versions table exist or not
   *
   * @param config the application config
   * @param ec the scala execution context
   * @return the name of the table if exist or null if not
   */
  def checkIfCosVersionTableExists(config: Config)(implicit ec: ExecutionContext): Future[Vector[String]] = {
    val schemaName: String = config.getString("jdbc-journal.tables.event_journal.schemaName")
    val dbconfig: DatabaseConfig[JdbcProfile] = JdbcConfig.getWriteSideConfig(config)
    val cosVersionTableNameLookupSql: String = s"$schemaName.cos_versions"
    dbconfig.db.run(
      sql"""SELECT to_regclass($cosVersionTableNameLookupSql)""".as[String]
    )
  }
}
