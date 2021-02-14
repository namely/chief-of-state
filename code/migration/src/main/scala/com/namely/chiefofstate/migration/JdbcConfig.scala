/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration

import com.typesafe.config.Config
import slick.basic.DatabaseConfig
import slick.jdbc.{JdbcProfile, PostgresProfile}

object JdbcConfig {

  /**
   * returns the write side database config
   *
   * @param config the main application config
   */
  def getWriteSideConfig(config: Config): DatabaseConfig[JdbcProfile] = {
    DatabaseConfig.forConfig[JdbcProfile]("write-side-slick", config)
  }

  /**
   * returns the read side databas config
   * @param config the main application config
   */
  def getReadSideConfig(config: Config): DatabaseConfig[PostgresProfile] = {
    DatabaseConfig.forConfig[PostgresProfile]("akka.projection.slick", config)
  }
}
