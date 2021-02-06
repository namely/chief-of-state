/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration
import com.typesafe.config.Config

/**
 * Placeholders value for the COS version 0.7.0
 * @param config the application configuration object
 */
case class SchemasPlaceholdersV070(config: Config) extends SchemasPlaceholders() {

  private val cosEventJournalPlaceholderValueKey: String = "jdbc-journal.tables.legacy_journal.tableName"
  private val cosStateSnapshotPlaceholderValueKey: String = "jdbc-snapshot-store.tables.legacy_snapshot.tableName"
  private val cosReadSideOffsetPlaceholderValueKey: String = "akka.projection.slick.offset-store.table"

  /**
   * returns the cos:journal placeholder value
   */
  override def eventJournalPlaceholderValue(): String = config.getString(cosEventJournalPlaceholderValueKey)

  /**
   * @return the cos:event_tag placeholder value
   */
  override def eventTagPlaceholderValue(): String = ""

  /**
   * returns cos:snapshot placeholder value
   *
   * @return string
   */
  override def stateSnapshotPlaceholderValue(): String = config.getString(cosStateSnapshotPlaceholderValueKey)

  /**
   *  returns cos:read_side_offsets_store
   *
   * @return string
   */
  override def readSideOffsetStorePlaceholderValue(): String = config.getString(cosReadSideOffsetPlaceholderValueKey)
}
