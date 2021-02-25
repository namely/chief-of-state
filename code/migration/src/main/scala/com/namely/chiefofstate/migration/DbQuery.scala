/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration

import com.typesafe.config.Config
import slick.basic.DatabaseConfig
import slick.jdbc.{GetResult, JdbcProfile}
import slick.jdbc.PostgresProfile.api._
import slick.sql.{SqlAction, SqlStreamingAction}

import scala.concurrent.{ExecutionContext, Future}

case class DbQuery(config: Config, journalJdbcConfig: DatabaseConfig[JdbcProfile])(implicit ec: ExecutionContext) {

  /**
   * checks the existence of the journal and snapshot tables in the given schema.
   */
  def checkIfLegacyTablesExist(): Future[(Vector[String], Vector[String])] = {
    val legacyJournalTableName: String = config.getString("jdbc-journal.tables.legacy_journal.tableName")
    val legacySnapshotTableName: String = config.getString("jdbc-snapshot-store.tables.legacy_snapshot.tableName")
    val legacyJournalSchemaName: String = config.getString("jdbc-journal.tables.legacy_journal.schemaName")
    val legacySnapshotSchemaName: String = config.getString("jdbc-snapshot-store.tables.legacy_snapshot.schemaName")

    val legacyJournalTableNameLookupSql: String = s"$legacyJournalSchemaName.$legacyJournalTableName"
    val legacySnapshotTableNameLookupSql: String = s"$legacySnapshotSchemaName.$legacySnapshotTableName"

    for {
      jname <- journalJdbcConfig.db.run(
        sql"""SELECT to_regclass($legacyJournalTableNameLookupSql)""".as[String]
      )
      sname <- journalJdbcConfig.db.run(
        sql"""SELECT to_regclass($legacySnapshotTableNameLookupSql)""".as[String]
      )
    } yield (jname, sname)
  }

  /**
   * checks the cos_versions table exist or not
   *
   * @return the name of the table if exist or null if not
   */
  def checkIfCosMigrationsTableExists(): Future[Vector[String]] = {
    val schemaName: String = config.getString("jdbc-journal.tables.event_journal.schemaName")
    val cosVersionTableNameLookupSql: String = s"$schemaName.cos_migrations"
    journalJdbcConfig.db.run(
      sql"""SELECT to_regclass($cosVersionTableNameLookupSql)""".as[String]
    )
  }

  def addCosMigrationHistory(migrationHistory: History): Future[Int] = {
    val sqlStmt: SqlAction[Int, NoStream, Effect] =
      sqlu"INSERT INTO cos_migrations VALUES (${migrationHistory.version}, ${migrationHistory.description}, ${migrationHistory.isSuccessful}, ${migrationHistory.cosVersion}, ${migrationHistory.created})"
    journalJdbcConfig.db.run(sqlStmt)
  }

  /**
   * retrieve the latest migration history inserted in the cos_migrations table
   * and return the result as [[History]] object
   *
   * @return [[History]] object
   */
  def latestMigrationHistory(): Future[Option[History]] = {
    implicit val getMigrationHistoryResult: AnyRef with GetResult[History] =
      GetResult(r => History(r.<<, r.<<, r.<<, r.<<, r.<<))

    val sqlStmt: SqlStreamingAction[Vector[History], History, Effect]#ResultAction[Option[
      History
    ], NoStream, Effect] =
      sql"""
            SELECT * FROM cos_migrations ORDER BY version DESC
        """.as[History].headOption
    journalJdbcConfig.db.run(sqlStmt)
  }
}
