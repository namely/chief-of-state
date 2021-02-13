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
import com.namely.chiefofstate.migration.legacy.JournalAndSnapshotMigration

object StartupBehaviour {

  def apply(cosConfig: CosConfig): Behavior[Nothing] = {
    Behaviors.setup[Nothing] { ctx =>
      if (cosConfig.createDataStores) {
        implicit val classicSys: ActorSystem = ctx.system.toClassic

        ctx.log.info("kick-starting the journal and snapshot store creation")
        val schemaUtils: SchemasUtil = SchemasUtil(ctx.system.settings.config)
        val migrationResult: Seq[String] = schemaUtils.createIfNotExists()

        // FIXME need to tidy it a bit. But for now it is ok for 0.8.0
        if (migrationResult.isEmpty) {
          ctx.log.info("no schema migration executed. :)")
        } else {
          val latestVersion: String = migrationResult.headOption.getOrElse("")
          val dataMigration: JournalAndSnapshotMigration = JournalAndSnapshotMigration(cosConfig.version)
          if (dataMigration.version.equals(latestVersion)) {
            dataMigration.migrate()
          } else {
            ctx.log.info("no data migration executed. :)")
          }
        }
      }
      Behaviors.empty
    }
  }
}
