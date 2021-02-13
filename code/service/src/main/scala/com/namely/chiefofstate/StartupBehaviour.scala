/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import com.namely.chiefofstate.config.CosConfig
import com.namely.chiefofstate.migration.SchemasUtil
import com.namely.chiefofstate.migration.legacy.{JournalMigrator, SnapshotMigrator}

object StartupBehaviour {
  def apply(cosConfig: CosConfig): Behavior[Nothing] = {
    Behaviors.setup[Nothing] { ctx =>
      if (cosConfig.createDataStores) {
        ctx.log.info("kick-starting the journal and snapshot store creation")
        val schemaUtils = SchemasUtil(ctx.system.settings.config)
        val migrationResult = schemaUtils.createIfNotExists()

        // the only time we do data migration is on version 0.8.0
        // TODO: find a better way to get data migration in
        val latestVersion = migrationResult.headOption.getOrElse("")
        if (latestVersion.equals("0.8.0")) {
          ctx.log.info("migration legacy journal and snapshot into the new journal and schemas")
          val journalMigrator = JournalMigrator(ctx.system.settings.config)(ctx.system.toClassic)
          val snapshotMigrator = SnapshotMigrator(ctx.system.settings.config)(ctx.system.toClassic)

          journalMigrator.migrate()
          snapshotMigrator.migrate()
        }
      }
      Behaviors.empty
    }
  }
}
