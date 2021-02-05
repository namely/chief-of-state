/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migrator

import com.typesafe.config.Config
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.Location
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.flywaydb.core.api.output.MigrateResult

import scala.jdk.CollectionConverters.{CollectionHasAsScala, MapHasAsJava}

/**
 * executes the database migration
 *
 * @param config the application config
 */
final case class FlywayMigrator(config: Config) {

  private val userKey: String = "slick.db.user"
  private val passwordKey: String = "slick.db.password"
  private val urlKey: String = "slick.db.url"

  // Flyway placeholders values keys
  private val cosEventJournalPlaceholderValueKey: String = "jdbc-journal.tables.event_journal.tableName"
  private val cosEventTagPlaceholderValueKey: String = "jdbc-journal.tables.event_tag.tableName"
  private val cosStateSnapshotPlaceholderValueKey: String = "jdbc-snapshot-store.tables.snapshot.tableName"
  private val cosReadSideOffsetPlaceholderValueKey: String = "akka.projection.slick.offset-store.table"

  // Flyway placeholders
  private val cosEventJournalPlaceholder = "cos:event_journal"
  private val cosEventTagPlaceholder: String = "cos:event_tag"
  private val cosStateSnapshotPlaceholder: String = "cos:state_snapshot"
  private val cosReadSideOffsetPlaceholder: String = "cos:read_side_offsets"

  private val url: String = config.getString(urlKey)
  private val user: String = config.getString(userKey)
  private val password: String = config.getString(passwordKey)

  /**
   * run the migrations
   *
   * @return the number of migration executed
   */
  def run(): Seq[String] = {
    val flywayConfig: FluentConfiguration = Flyway.configure
      .dataSource(url, user, password)
      .table("cos_schema_history")
      .locations(new Location("classpath:db/migration/postgres"))
      .ignoreMissingMigrations(true)
      .placeholders(
        Map(
          cosEventJournalPlaceholder -> config.getString(cosEventJournalPlaceholderValueKey),
          cosEventTagPlaceholder -> config.getString(cosEventTagPlaceholderValueKey),
          cosStateSnapshotPlaceholder -> config.getString(cosStateSnapshotPlaceholderValueKey),
          cosReadSideOffsetPlaceholder -> config.getString(cosReadSideOffsetPlaceholderValueKey)
        ).asJava
      )
      .baselineVersion("0.7.0")

    val flyway: Flyway = flywayConfig.load
    flyway.baseline()
    // running the migration
    val result: MigrateResult = flyway.migrate()

    // let us return the sequence of migration versions applied sorted in a descending order
    result.migrations.asScala.toSeq.map(_.version).sortWith(_ > _)
  }
}
