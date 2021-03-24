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
  // get a typesafe config for a given jdbc url
  def getTypesafeConfigUrl(rootKey: String = "jdbc-default", url: String, user: String, password: String): Config = {

    val cfgString: String = s"""
      $rootKey {
        profile = "slick.jdbc.PostgresProfile$$"
        db {
          connectionPool = disabled
          driver = "org.postgresql.Driver"
          user = "$user"
          password = "$password"
          url = "$url"
        }
      }
    """

    ConfigFactory.parseString(cfgString)
  }

  def getProjectionConfig(url: String, user: String, password: String, useLowerCase: Boolean): Config = {
    val cfgString: String = s"""
      akka.projection.slick {
        profile = "slick.jdbc.PostgresProfile$$"
        db {
          connectionPool = disabled
          driver = "org.postgresql.Driver"
          user = "$user"
          password = "$password"
          url = "$url"
        }
        offset-store {
          table = "read_side_offsets"
          use-lowercase-schema = $useLowerCase
        }
      }
    """

    ConfigFactory.parseString(cfgString)
  }

  // construct a database config for a given jdbc url
  def dbConfigFromUrl(url: String,
                      user: String,
                      password: String,
                      rootKey: String = "jdbc-default"
  ): DatabaseConfig[JdbcProfile] = {
    val cfg = getTypesafeConfigUrl(rootKey, url, user, password)
    DatabaseConfig.forConfig[JdbcProfile](rootKey, cfg)
  }
}
