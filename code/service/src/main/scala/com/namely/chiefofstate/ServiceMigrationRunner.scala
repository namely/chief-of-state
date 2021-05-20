/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.namely.chiefofstate.migration.{ JdbcConfig, Migrator }
import com.namely.chiefofstate.migration.versions.v1.V1
import com.namely.chiefofstate.migration.versions.v2.V2
import com.namely.chiefofstate.migration.versions.v3.V3
import com.namely.chiefofstate.migration.versions.v4.V4
import com.namely.chiefofstate.migration.versions.v5.V5
import com.namely.chiefofstate.migration.versions.v6.V6
import com.namely.chiefofstate.serialization.{ MessageWithActorRef, ScalaMessage }
import com.namely.protobuf.chiefofstate.v1.internal.{ DoMigration, MigrationDone }
import com.typesafe.config.Config
import org.slf4j.{ Logger, LoggerFactory }
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.util.{ Failure, Success, Try }

/**
 * kick starts the various migrations needed to run.
 * When the migration process is successful it replies back to the [[ServiceBootstrapper]] to continue the boot process.
 * However when the migration process fails it stops and the [[StartNodeBehaviour]] get notified and halt the whole
 * boot process. This is the logic behind running the actual migration
 * <p>
 *   <ol>
 *     <li> check the existence of the cos_migrations table
 *     <li> if the table exists go to step 4
 *     <li> if the table does not exist run the whole migration and reply back to the [[ServiceBootstrapper]]
 *     <li> check the current version against the available versions
 *     <li> if current version is the last version then no need to run the migration just reply back to [[ServiceBootstrapper]]
 *     <li> if not then run the whole migration and reply back to the [[ServiceBootstrapper]]
 *   </ol>
 * </p>
 */
object ServiceMigrationRunner {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  def apply(config: Config): Behavior[ScalaMessage] = Behaviors.setup[ScalaMessage] { context =>
    Behaviors.receiveMessage[ScalaMessage] {
      case MessageWithActorRef(message, replyTo) if message.isInstanceOf[DoMigration] =>
        val result: Try[Unit] = {
          // create and run the migrator
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
          val v4: V4 = V4(journalJdbcConfig)
          val v5: V5 = V5(context.system, journalJdbcConfig)
          val v6: V6 = V6(journalJdbcConfig, config.getString("jdbc-default.schema"))

          // instance of the migrator
          val migrator: Migrator =
            new Migrator(journalJdbcConfig)
              .addVersion(v1)
              .addVersion(v2)
              .addVersion(v3)
              .addVersion(v4)
              .addVersion(v5)
              .addVersion(v6)

          migrator.run()
        }

        result match {
          case Failure(exception) =>
            log.error("migration failed", exception)

            // stopping the actor
            Behaviors.stopped

          case Success(_) =>
            log.info("ChiefOfState migration successfully done...")

            replyTo ! MigrationDone.defaultInstance

            Behaviors.same
        }

      case MessageWithActorRef(message, _) =>
        log.warn(s"unhandled message ${message.companion.scalaDescriptor.fullName}")
        Behaviors.stopped

      case unhandled =>
        log.warn(s"cannot serialize ${unhandled.getClass.getName}")

        Behaviors.stopped
    }
  }
}
