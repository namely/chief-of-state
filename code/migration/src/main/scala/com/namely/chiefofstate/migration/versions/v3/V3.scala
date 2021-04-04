/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v3

import com.namely.chiefofstate.migration.Version
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

/**
 * V3 removes the string prefixes from persistence ID's and tags.
 *
 * @param projectionJdbcConfig the projection configuration
 */
case class V3(
  journalJdbcConfig: DatabaseConfig[JdbcProfile]
) extends Version {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  override def versionNumber: Int = 3

  /**
   * Runs the upgrade, which uses update statements and regex to
   * remove the prefixes from old events
   *
   * @return a DBIO that runs this upgrade
   */
  override def upgrade(): DBIO[Unit] = {
    log.info(s"running upgrade for version #$versionNumber")

    DBIO.seq(
      // remove "chiefOfState" prefix from journal
      sqlu"""
        UPDATE event_journal
        SET persistence_id = regexp_replace(persistence_id, '^chiefOfState\|', '')
      """,
      // remove "chiefofstate" prefix from tags
      sqlu"""
        UPDATE event_tag
        SET tag = regexp_replace(tag, '^chiefofstate', '')
      """
    )
  }

  /**
   * creates the latest COS schema if no prior versions found.
   *
   * @return a DBIO that creates the version snapshot
   */
  override def snapshot(): DBIO[Unit] = {
    log.info(s"running snapshot for version #$versionNumber")
    SchemasUtil.createJournalTables(journalJdbcConfig)
    DBIO.successful {}
  }
}
