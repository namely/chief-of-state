package com.namely.chiefofstate.migrator.data

import com.typesafe.config.Config
import slick.migration.api.SqlMigration

case class LegacySnapshotMigration(config: Config) extends SqlMigration() {
  override def sql: Seq[String] = ???
}
