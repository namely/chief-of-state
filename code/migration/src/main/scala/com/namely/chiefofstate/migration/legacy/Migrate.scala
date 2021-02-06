package com.namely.chiefofstate.migration.legacy

import akka.actor.ActorSystem
import com.typesafe.config.Config
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

abstract class Migrate(config: Config)(implicit system: ActorSystem) {
  // let us get the database configuration
  protected val dbconfig = DatabaseConfig.forConfig[JdbcProfile]("write-side-slick", config)
  protected val profile = dbconfig.profile
}
