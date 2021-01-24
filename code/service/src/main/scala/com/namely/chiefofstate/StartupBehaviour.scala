/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.persistence.jdbc.testkit.scaladsl.SchemaUtils
import com.namely.chiefofstate.config.CosConfig

object StartupBehaviour {
  def apply(cosConfig: CosConfig): Behavior[Nothing] = {
    Behaviors.setup[Nothing] { ctx =>
      if (cosConfig.createDataStores) {
        ctx.log.info("kick-starting the journal and snapshot store creation")
        SchemaUtils.createIfNotExists()(ctx.system)
      }
      Behaviors.empty
    }
  }
}
