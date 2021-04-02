/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.namely.chiefofstate.migration.{JdbcConfig, Migrator}
import com.namely.chiefofstate.migration.versions.v1.V1
import com.namely.chiefofstate.migration.versions.v2.V2
import com.namely.chiefofstate.migration.versions.v3.V3
import com.namely.chiefofstate.ServiceBootstrapper.{StartCommand, StartMigration, StartMigrationReply}
import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.util.{Failure, Success}

/**
 * kick starts the various migrations needed to run.
 * When the migration process is successful it pusblishes a ready message to a topic letting the [[StartNodeBehaviour]] to continue the boot process.
 * However when the migration process fails it stops and the [[StartNodeBehaviour]] get notified and halt the whole
 * boot process
 */
object ServiceMigrationRunner {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  def apply(config: Config): Behavior[StartCommand] = Behaviors
    .setup[StartCommand] { context =>
      Behaviors.receiveMessage[StartCommand] {
        case StartMigration(replyTo) =>
          // create and run the migrator
          val migrator: Migrator = {
            val journalJdbcConfig: DatabaseConfig[JdbcProfile] =
              JdbcConfig.journalConfig(config)

            // for the migration, as COS projections has moved to raw JDBC
            // connections and no longer uses slick.
            val projectionJdbcConfig: DatabaseConfig[JdbcProfile] =
              JdbcConfig.projectionConfig(config)

            // get the projection config
            val priorProjectionJdbcConfig: DatabaseConfig[JdbcProfile] =
              JdbcConfig.projectionConfig(config, "chiefofstate.migration.v1.slick")

            // get the old table name
            val priorOffsetStoreName: String =
              config.getString("chiefofstate.migration.v1.slick.offset-store.table")

            val v1: V1 = V1(journalJdbcConfig, priorProjectionJdbcConfig, priorOffsetStoreName)
            val v2: V2 = V2(journalJdbcConfig, projectionJdbcConfig)(context.system)
            val v3: V3 = V3(journalJdbcConfig)

            new Migrator(journalJdbcConfig)
              .addVersion(v1)
              .addVersion(v2)
              .addVersion(v3)
          }

          migrator.run() match {
            case Failure(exception) =>
              log.error("migration failed", exception)
              // stopping the actor
              Behaviors.stopped
            case Success(_) =>
              log.info("ChiefOfState migration successfully done. About to start...")
              replyTo ! StartMigrationReply()
              Behaviors.same
          }
        case _ => Behaviors.stopped
      }
    }
}
