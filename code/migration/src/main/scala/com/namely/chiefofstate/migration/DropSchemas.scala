/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration

import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

/**
 * @param config the application config
 * @param journalJdbcConfig the journal Jdbc config
 */
case class DropSchemas(journalJdbcConfig: DatabaseConfig[JdbcProfile]) {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Drops the legacy journal and snapshot tables
   *
   * @param config the application config
   * @return true when successful or an exception
   */
  def legacyJournalStoresIfExist(): Future[Unit] = {
    journalJdbcConfig.db.run(
      DBIO
        .seq(
          sqlu"""
          DROP TABLE IF EXISTS journal CASCADE
      """,
          sqlu"""
          DROP TABLE IF EXISTS snapshot CASCADE
      """
        )
        .withPinnedSession
        .transactionally
    )
  }

  /**
   * Drops the journal and snapshot stores
   */
  def journalStoresIfExist(): Future[Unit] = {
    journalJdbcConfig.db.run(
      DBIO
        .seq(
          sqlu"""
          DROP TABLE IF EXISTS event_journal CASCADE
      """,
          sqlu"""
          DROP TABLE IF EXISTS state_snapshot CASCADE
      """
        )
        .withPinnedSession
        .transactionally
    )
  }
}
