/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration
import com.typesafe.config.Config
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.Location
import org.flywaydb.core.api.output.MigrateResult
import org.slf4j.{Logger, LoggerFactory}

import scala.jdk.CollectionConverters.{CollectionHasAsScala, MapHasAsJava}

/**
 * SchemasUtils help create all the required schemas needed to smoothly run COS
 *
 * @param config the application configuration
 */
final case class SchemasUtil(config: Config) {
  val log: Logger = LoggerFactory.getLogger(getClass)

  // With COS both read model and write model share the same database
  private val userKey: String = "write-side-slick.db.user"
  private val passwordKey: String = "write-side-slick.db.password"
  private val urlKey: String = "write-side-slick.db.url"

  // Flyway placeholders
  private val cosEventJournalPlaceholder = "cos:journal"
  private val cosEventTagPlaceholder: String = "cos:event_tag"
  private val cosStateSnapshotPlaceholder: String = "cos:snapshot"
  private val cosReadSideOffsetPlaceholder: String = "cos:read_side_offsets_store"

  private val cosEventJournalPlaceholderValueKey: String = "jdbc-journal.tables.event_journal.tableName"
  private val cosEventTagPlaceholderValueKey: String = "jdbc-journal.tables.event_tag.tableName"
  private val cosStateSnapshotPlaceholderValueKey: String = "jdbc-snapshot-store.tables.snapshot.tableName"
  private val cosReadSideOffsetPlaceholderValueKey: String = "akka.projection.slick.offset-store.table"

  // database credentials
  private val url: String = config.getString(urlKey)
  private val user: String = config.getString(userKey)
  private val password: String = config.getString(passwordKey)

  /**
   * Creates the schema for both the journal, the snapshot and the readside offset store table using the default schema definition.
   * This method will automatically detects the configured database using the settings from `jdbc-journal`,
   * `jdbc-snapshot-store` and `akka.projection.slick.offset-store` configs
   *
   * @return  true when successful or false when it failed
   */
  def createIfNotExists(): Seq[String] = {
    val flywayConfig: FluentConfiguration = Flyway.configure
      .dataSource(url, user, password)
      .table("cos_schema_history")
      .locations(new Location("classpath:db/migration/postgres"))
      .ignoreMissingMigrations(true)
      .validateMigrationNaming(true)
      .placeholders(
        Map(
          cosEventJournalPlaceholder -> config.getString(cosEventJournalPlaceholderValueKey),
          cosEventTagPlaceholder -> config.getString(cosEventTagPlaceholderValueKey),
          cosStateSnapshotPlaceholder -> config.getString(cosStateSnapshotPlaceholderValueKey),
          cosReadSideOffsetPlaceholder -> config.getString(cosReadSideOffsetPlaceholderValueKey)
        ).asJava
      )
      .baselineVersion("0.0.0") // this is required because we started using flyway at this version of COS

    val flyway: Flyway = flywayConfig.load
    flyway.baseline()

    // running the migration
    log.info("setting up chiefofstate stores....")
    val result: MigrateResult = flyway.migrate()

    result.migrations.asScala.toSeq.map(_.version).sortWith(_ > _)
  }
}
