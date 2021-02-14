/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate
import akka.actor.typed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import com.namely.chiefofstate.config.CosConfig
import com.namely.chiefofstate.migration.legacy.{JournalAndSnapshotMigration, OffsetStoreMigration}
import com.namely.chiefofstate.migration.CosSchemas

import scala.concurrent.ExecutionContextExecutor

object StartupBehaviour {

  def apply(cosConfig: CosConfig): Behavior[Nothing] = {
    Behaviors.setup[Nothing] { ctx =>
      if (cosConfig.createDataStores) {
        implicit val sys: typed.ActorSystem[Nothing] = ctx.system
        implicit val ec: ExecutionContextExecutor = sys.executionContext

        ctx.log.info("kick-starting the chiefofstate journal, snapshot and read side offset stores creation")

        CosSchemas
          .createIfNotExists(ctx.system.settings.config)
          .map(_ => {
            ctx.log.info("chiefofstate journal, snapshot and read side offset stores created. :)")
          })

        // FIXME need to tidy it a bit. But for now it is ok for 0.8.0
        if (cosConfig.version.equals("0.8.0")) {
          OffsetStoreMigration.renameColumns(sys.settings.config)
          JournalAndSnapshotMigration()(sys.toClassic).migrate()
        }
      }
      Behaviors.empty
    }
  }
}
