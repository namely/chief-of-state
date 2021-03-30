/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.config

import com.namely.chiefofstate.helper.BaseSpec
import com.typesafe.config.{Config, ConfigException, ConfigFactory}

class EventsConfigSpec extends BaseSpec {
  "Loading events config" should {
    "be successful when all is set" in {
      val config: Config = ConfigFactory.parseString(s"""
            akka.cluster.sharding.number-of-shards = 2
          """)

      EventsConfig(config) shouldBe EventsConfig(2)

      noException shouldBe thrownBy(EventsConfig(config))
    }

    "fail when any of the settings is missing or not properly set" in {
      val config: Config = ConfigFactory.parseString(s"""
            akka.cluster.sharding.number-of-poops = 2
          """)
      an[ConfigException] shouldBe thrownBy(EventsConfig(config))
    }
  }
}
