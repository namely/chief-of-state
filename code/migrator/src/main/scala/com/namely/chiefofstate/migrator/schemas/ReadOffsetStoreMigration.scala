package com.namely.chiefofstate.migrator.schemas

import slick.migration.api.SqlMigration

/**
 * creates the readside offset store migration SQL statement
 *
 * @param tableName the readiside offset store table name
 */
case class ReadOffsetStoreMigration(tableName: String) extends SqlMigration {
  override def sql: Seq[String] = Seq(
    s"""
     CREATE TABLE IF NOT EXISTS $tableName (
      projection_name VARCHAR(255) NOT NULL,
      projection_key VARCHAR(255) NOT NULL,
      current_offset VARCHAR(255) NOT NULL,
      manifest VARCHAR(4) NOT NULL,
      mergeable BOOLEAN NOT NULL,
      last_updated BIGINT NOT NULL,
      PRIMARY KEY(projection_name, projection_key)
    );
    """
  )
}
