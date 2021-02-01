package com.namely.chiefofstate.migrator.schemas

import slick.migration.api.SqlMigration

/**
 * creates the migration SQL statement for the snapshot
 *
 * @param tableName the snapshot table name
 */
case class SnapshotMigration(tableName: String) extends SqlMigration {
  override def sql: Seq[String] = Seq(
    s"""
      CREATE TABLE IF NOT EXISTS $tableName (
      persistence_id VARCHAR(255) NOT NULL,
      sequence_number BIGINT NOT NULL,
      created BIGINT NOT NULL,

      snapshot_ser_id INTEGER NOT NULL,
      snapshot_ser_manifest VARCHAR(255) NOT NULL,
      snapshot_payload BYTEA NOT NULL,

      meta_ser_id INTEGER,
      meta_ser_manifest VARCHAR(255),
      meta_payload BYTEA,

      PRIMARY KEY(persistence_id, sequence_number)
     )
     """
  )
}
