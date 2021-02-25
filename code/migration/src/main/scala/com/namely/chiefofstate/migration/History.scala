/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration

/**
 * Maps to the cos_migrations table
 *
 * @param version the migration version
 * @param description the migration description
 * @param isSuccessful the state of the migration
 * @param cosVersion the cos_version of that migration
 * @param created the date and time the migration is executed
 */
case class History(
  version: String,
  description: String,
  isSuccessful: Boolean,
  cosVersion: String,
  created: Int
)
