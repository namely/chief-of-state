/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.config

import com.typesafe.config.Config

/**
 * Events settings
 *
 * @param eventTag the event tag name
 */
final case class EventsConfig(numShards: Int)
object EventsConfig {
  private val numShardsKey = "akka.cluster.sharding.number-of-shards"

  /**
   * creates a  new instance of EventsConfif
   * @param config the config object
   * @return the new instance created
   */
  def apply(config: Config): EventsConfig = {
    EventsConfig(numShards = config.getInt(numShardsKey))
  }
}
