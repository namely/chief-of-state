/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.adapter._
import com.namely.chiefofstate.config.CosConfig
import com.namely.chiefofstate.migrator.{FlywayMigrator, LegacyJournalDataMigrator}

object StartupBehaviour {

  def apply(cosConfig: CosConfig): Behavior[Nothing] = {
    Behaviors.setup[Nothing] { ctx =>
      if (cosConfig.createDataStores) {
        ctx.log.info("kick-starting the journal and snapshot store creation")
        val versionsRun: Seq[String] = FlywayMigrator(ctx.system.settings.config).run()

        // only run the legacy journal migrator when the migration version is 0.8.0
        if (versionsRun.head.equals("0.8.0")) {
          ctx.log.info("migrating journal data from legacy system...")
          LegacyJournalDataMigrator(ctx.system.settings.config)(ctx.system.toClassic).writeEvents()
        }
      }
      Behaviors.empty
    }
  }
}
