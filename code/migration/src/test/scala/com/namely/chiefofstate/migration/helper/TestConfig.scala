/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.helper

import com.typesafe.config.{Config, ConfigFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

object TestConfig {
  def getTypesafeConfig(schemaName: String, rootKey: String = "jdbc-default"): Config = {

    val cfgString: String = s"""
      $rootKey {
        profile = "slick.jdbc.PostgresProfile$$"
        db {
          connectionPool = disabled
          driver = "org.postgresql.Driver"
          user = "postgres"
          password = "changeme"
          serverName = "locahost"
          portNumber = 25432
          databaseName = "postgres"
          schemaName = "$schemaName"
          url = "jdbc:postgresql://localhost:25432/postgres?currentSchema=$schemaName"
        }
      }
    """

    ConfigFactory.parseString(cfgString)
  }

  def getDbConfig(schemaName: String): DatabaseConfig[JdbcProfile] = {
    val cfg = getTypesafeConfig(schemaName)
    DatabaseConfig.forConfig[JdbcProfile]("jdbc-default", cfg)
  }
}
