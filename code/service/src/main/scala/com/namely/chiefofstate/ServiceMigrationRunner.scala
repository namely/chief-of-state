/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.namely.chiefofstate.migration.{DbUtil, JdbcConfig, Migrator}
import com.namely.chiefofstate.migration.versions.v1.V1
import com.namely.chiefofstate.migration.versions.v2.V2
import com.namely.chiefofstate.migration.versions.v3.V3
import com.namely.chiefofstate.serialization.{MessageWithActorRef, ScalaMessage}
import com.namely.protobuf.chiefofstate.v1.internal.{DoMigration, MigrationDone}
import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.util.{Failure, Success, Try}

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

  def apply(config: Config): Behavior[ScalaMessage] = Behaviors
    .setup[ScalaMessage] { context =>
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

            // instance of the migrator
            val migrator: Migrator = new Migrator(journalJdbcConfig)
              .addVersion(v1)
              .addVersion(v2)
              .addVersion(v3)

            // check whether the migration table exists or not
            // TODO move this implementation into the migrator
            if (!DbUtil.tableExists(journalJdbcConfig, Migrator.COS_MIGRATIONS_TABLE)) {
              migrator.run()
            } else {
              // get the last version
              val lastVersion: Option[Int] = migrator.getVersions().lastOption.map(_.versionNumber)

              // get the current version
              val currentVersion: Option[Int] = Migrator.getCurrentVersionNumber(journalJdbcConfig)

              // get the ordering
              val ordering: Ordering[Option[Int]] = implicitly[Ordering[Option[Int]]]
              // TODO move this implementation into the migrator
              if (ordering.lt(currentVersion, lastVersion)) {
                migrator.run()
              } else {
                log.info("ChiefOfState migration already run")
                Success {}
              }
            }
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
