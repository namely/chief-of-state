/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v1

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

private[migration] object V1Schema {
  val createJournal: DBIOAction[Unit, NoStream, Effect] = {
    val createTable = sqlu"""
     CREATE TABLE journal (
      ordering        BIGSERIAL,
      persistence_id  VARCHAR(255) NOT NULL,
      sequence_number BIGINT       NOT NULL,
      deleted         BOOLEAN      DEFAULT FALSE,
      tags            VARCHAR(255) DEFAULT NULL,
      message         BYTEA        NOT NULL,
      PRIMARY KEY (persistence_id, sequence_number)
     )"""

    val createIx = sqlu"""
      CREATE UNIQUE INDEX journal_ordering_idx on journal (ordering)
    """

    DBIO.seq(createTable, createIx)
  }

  val createSnapshot: DBIOAction[Unit, NoStream, Effect] = {
    val stmt = sqlu"""
    CREATE TABLE snapshot (
      persistence_id  VARCHAR(255) NOT NULL,
      sequence_number BIGINT       NOT NULL,
      created         BIGINT       NOT NULL,
      snapshot        BYTEA        NOT NULL,
      PRIMARY KEY (persistence_id, sequence_number)
    )"""
    stmt.andThen(DBIO.successful {})
  }
}
