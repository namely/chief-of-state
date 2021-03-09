/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration

import slick.basic.DatabaseConfig
import slick.jdbc.meta.MTable
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object DbUtil {

  /**
   * returns true if a given table exists in the given DatabaseConfig
   *
   * @param dbConfig a JDBC DatabaseConfig
   * @param tableName the table name to search for
   * @return true if the table exists
   */
  def tableExists(dbConfig: DatabaseConfig[JdbcProfile], tableName: String): Boolean = {
    val tables: Seq[MTable] = Await.result(dbConfig.db.run(MTable.getTables), Duration.Inf)
    tables
      .filter(_.tableType == "TABLE")
      .exists(_.name.name.equals(tableName))
  }

  /**
   * helps drop a table
   *
   * @param tableName the table name
   * @param dbConfig the database config
   */
  def dropTableIfExists(tableName: String, dbConfig: DatabaseConfig[JdbcProfile]): Int = {
    Await.result(
      dbConfig.db.run(
        sqlu"""DROP TABLE IF EXISTS #$tableName CASCADE""".withPinnedSession.transactionally
      ),
      Duration.Inf
    )
  }

}
