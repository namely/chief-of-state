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

import scala.jdk.CollectionConverters.MapHasAsJava

/**
 * SchemasUtils help create all the required schemas needed to smoothly run COS
 *
 * @param config the application configuration
 */
case class SchemasUtil(config: Config, placeholders: SchemasPlaceholders) {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  // With COS both read model and write model share the same database
  private val userKey: String = "write-side-slick.db.user"
  private val passwordKey: String = "write-side-slick.db.password"
  private val urlKey: String = "write-side-slick.db.url"

  // Flyway placeholders
  private val cosEventJournalPlaceholder = "cos:journal"
  private val cosEventTagPlaceholder: String = "cos:event_tag"
  private val cosStateSnapshotPlaceholder: String = "cos:snapshot"
  private val cosReadSideOffsetPlaceholder: String = "cos:read_side_offsets_store"

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
  def createIfNotExists(): Boolean = {
    val flywayConfig: FluentConfiguration = Flyway.configure
      .dataSource(url, user, password)
      .table("cos_schema_history")
      .locations(new Location("classpath:db/migration/postgres"))
      .ignoreMissingMigrations(true)
      .placeholders(
        Map(
          cosEventJournalPlaceholder -> placeholders.eventJournalPlaceholderValue,
          cosEventTagPlaceholder -> placeholders.eventTagPlaceholderValue,
          cosStateSnapshotPlaceholder -> placeholders.stateSnapshotPlaceholderValue,
          cosReadSideOffsetPlaceholder -> placeholders.readSideOffsetStorePlaceholderValue
        ).asJava
      )
      .baselineVersion("0.7.0") // this is required because we started using flyway at this version of COS

    val flyway: Flyway = flywayConfig.load
    flyway.baseline()

    // running the migration
    log.info("setting up chiefofstate stores....")
    val result: MigrateResult = flyway.migrate()
    if (result.migrationsExecuted >= 0) {
      log.info("chiefofstate stores successfully set..:)")
      true
    } else {
      log.warn("unable to set up chiefofstate stores..")
      false
    }
  }
}
