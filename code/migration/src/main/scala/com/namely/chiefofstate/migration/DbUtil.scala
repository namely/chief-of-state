/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import slick.dbio.DBIO
import slick.jdbc.meta.MTable
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

object DbUtil {

  /**
   * returns true if a given table exists in the given DatabaseConfig
   *
   * @param dbConfig a JDBC DatabaseConfig
   * @param tableName the table name to search for
   * @return true if the table exists
   */
  def tableExists(dbConfig: DatabaseConfig[JdbcProfile], tableName: String): Boolean = {
    val tables = Await.result(dbConfig.db.run(MTable.getTables), Duration.Inf)
    tables.exists(_.name.name.equals(tableName))
  }

}
