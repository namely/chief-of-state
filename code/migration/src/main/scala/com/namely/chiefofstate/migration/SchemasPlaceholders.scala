/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration

/**
 * Placeholders help define the flyway migration placeholders values.
 * This help to properly set the write tables name defined by the user.
 */
trait SchemasPlaceholders {

  /**
   * returns the cos:journal placeholder value
   */
  def eventJournalPlaceholderValue(): String

  /**
   * @return the cos:event_tag placeholder value
   */
  def eventTagPlaceholderValue(): String

  /**
   * returns cos:snapshot placeholder value
   *
   * @return string
   */
  def stateSnapshotPlaceholderValue(): String

  /**
   *  returns cos:read_side_offsets_store
   *
   * @return string
   */
  def readSideOffsetStorePlaceholderValue(): String
}
