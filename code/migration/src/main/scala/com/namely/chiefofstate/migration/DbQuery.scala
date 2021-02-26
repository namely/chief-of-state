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
import slick.jdbc.meta.MTable
import slick.sql.SqlStreamingAction

import scala.concurrent.{ExecutionContext, Future}

case class DbQuery(config: Config, journalJdbcConfig: DatabaseConfig[JdbcProfile])(implicit ec: ExecutionContext) {

  /**
   * checks the existence of the journal and snapshot tables in the given schema.
   */
  def checkIfLegacyTablesExist(): Future[Boolean] = {
    // journal tables
    val journalTables: Seq[String] = Seq(
      config.getString("jdbc-journal.tables.legacy_journal.tableName"),
      config.getString("jdbc-snapshot-store.tables.legacy_snapshot.tableName")
    )

    // get the tables lookup query
    val query: DBIOAction[Boolean, NoStream, Effect.Read with Effect with Effect.Transactional] = MTable.getTables
      .flatMap { tables =>
        DBIO.successful(journalTables.forall(tables.map(_.name.name).contains))
      }
      .withPinnedSession
      .transactionally

    journalJdbcConfig.db.run(query)
  }

  /**
   * checks the cos_versions table exist or not
   *
   * @return the name of the table if exist or null if not
   */
  def checkIfCosMigrationsTableExists(): Future[Boolean] = {
    // get the table lookup query
    val query: DBIOAction[Boolean, NoStream, Effect.Read with Effect with Effect.Transactional] = MTable.getTables
      .flatMap { tables =>
        DBIO.successful(tables.exists(_.name.name.equals("cos_migrations")))
      }
      .withPinnedSession
      .transactionally

    journalJdbcConfig.db.run(query)
  }

  def addCosMigrationHistory(migrationHistory: History): Future[Int] = {
    val sqlStmt: DBIOAction[Int, NoStream, Effect] =
      sqlu"INSERT INTO cos_migrations VALUES (${migrationHistory.version}, ${migrationHistory.description}, ${migrationHistory.isSuccessful}, ${migrationHistory.cosVersion}, ${migrationHistory.created})".withPinnedSession
    journalJdbcConfig.db.run(sqlStmt)
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
            SELECT id, version, description, is_successful, cos_version, created
              FROM cos_migrations ORDER BY version DESC
        """.as[History].headOption
    journalJdbcConfig.db.run(sqlStmt)
  }
}
