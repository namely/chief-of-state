/*
 * MIT License
 *
 * Copyright (c) 2020 Namely
 */

package com.namely.chiefofstate
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import com.typesafe.config.Config

object StartupBehaviour {
  def apply(config: Config, createStore: Boolean): Behavior[Nothing] = {
    Behaviors.setup[Nothing] { ctx =>
      if (createStore) {
        ctx.log.info("kick-starting the journal and snapshot store creation")
        val migration: JournalAndSnapshotMigration = JournalAndSnapshotMigration(config)
        migration.createSchemas()
      }
      Behaviors.empty
    }
  }
}
