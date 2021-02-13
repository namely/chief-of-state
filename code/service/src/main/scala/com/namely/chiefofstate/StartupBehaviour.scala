/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.actor.ActorSystem
import com.namely.chiefofstate.config.CosConfig
import com.namely.chiefofstate.migration.SchemasUtil
import com.namely.chiefofstate.migration.legacy.{JournalMigrator, SnapshotMigrator}

import scala.concurrent.{ExecutionContextExecutor, Future}

object StartupBehaviour {
  def apply(cosConfig: CosConfig): Behavior[Nothing] = {
    Behaviors.setup[Nothing] { ctx =>
      if (cosConfig.createDataStores) {
        implicit val classicSys: ActorSystem = ctx.system.toClassic
        implicit val ec: ExecutionContextExecutor = ctx.system.executionContext

        ctx.log.info("kick-starting the journal and snapshot store creation")
        val schemaUtils: SchemasUtil = SchemasUtil(ctx.system.settings.config)
        val migrationResult: Seq[String] = schemaUtils.createIfNotExists()

        // the only time we do data migration is on version 0.8.0
        // TODO: find a better way to get data migration in
        val latestVersion: String = migrationResult.headOption.getOrElse("")
        if (latestVersion.equals("0.8.0")) {

          ctx.log.info("migration legacy journal and snapshot into the new journal and schemas")
          val journalMigrator: JournalMigrator = JournalMigrator(ctx.system.settings.config)
          val snapshotMigrator: SnapshotMigrator = SnapshotMigrator(ctx.system.settings.config)

          Future
            .sequence(List(journalMigrator.migrate(), snapshotMigrator.migrate()))
            .map(_ => ())
        }
      }
      Behaviors.empty
    }
  }
}
