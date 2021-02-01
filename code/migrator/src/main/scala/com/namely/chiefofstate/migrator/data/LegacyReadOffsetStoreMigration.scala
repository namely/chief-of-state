package com.namely.chiefofstate.migrator.data

import com.typesafe.config.Config
import slick.migration.api.SqlMigration

case class LegacyReadOffsetStoreMigration(config: Config, tableName: String) extends SqlMigration {
  override def sql: Seq[String] = ???
}
