/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import akka.NotUsed
import akka.actor.typed.ActorSystem
import com.namely.chiefofstate.config.BootConfig
import com.typesafe.config.Config

object StartNode extends App {
  // Application config
  val config: Config = BootConfig.get()

  // boot the actor system
  val actorSystem: ActorSystem[NotUsed] =
    ActorSystem(StartNodeBehaviour(config), "ChiefOfStateSystem", config)
  actorSystem.whenTerminated // remove compiler warnings
}
