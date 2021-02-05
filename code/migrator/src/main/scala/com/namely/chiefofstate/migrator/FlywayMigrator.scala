package com.namely.chiefofstate.migrator

import com.typesafe.config.Config
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.Location
import org.flywaydb.core.api.configuration.FluentConfiguration

/**
 * executes the database migration
 *
 * @param config the application config
 */
final case class FlywayMigrator(config: Config) {

  private val userKey: String = "write-side-slick.db.user"
  private val passwordKey: String = "write-side-slick.db.password"
  private val urlKey: String = "write-side-slick.db.url"

  private val url: String = config.getString(urlKey)
  private val user: String = config.getString(userKey)
  private val password: String = config.getString(passwordKey)

  /**
   * run the migrations
   *
   * @return the number of migration executed
   */
  def run(): Int = {
    val flywayConfig: FluentConfiguration = Flyway.configure
      .dataSource(url, user, password)
      .table("cos_schema_history")
      .locations(new Location("classpath:db/migration/postgres"))
      .ignoreMissingMigrations(true)

    val flyway: Flyway = flywayConfig.load
    flyway.baseline()
    flyway.migrate().migrationsExecuted
  }
}
