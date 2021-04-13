/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.config

import com.namely.chiefofstate.helper.{ BaseSpec, EnvironmentHelper }

class ReadSideConfigSpec extends BaseSpec {

  override def beforeEach(): Unit = {
    super.beforeEach()
    EnvironmentHelper.clearEnv()
  }

  val settingName: String = "test"

  "ReadSide config" should {
    "help set or retrieve value" in {
      val value: String = "foo"
      val config: ReadSideConfig = ReadSideConfig("test")
      // Gets unset key
      config.getSetting(settingName) shouldBe None
      // Adds key
      val addition: ReadSideConfig = config.addSetting(settingName, value)
      // Gets new key value
      addition.getSetting(settingName) shouldBe (Some(value))
      // Removes key
      val removed: ReadSideConfig = addition.removeSetting(settingName)
      // Gets removed key
      removed.getSetting(settingName) shouldBe None
    }

    "return all settings" in {
      val config: ReadSideConfig =
        ReadSideConfig("test").addSetting("foo", "foo").addSetting("bar", "bar").addSetting("baz", "baz")
      config.listSettings should contain theSameElementsAs Map("foo" -> "foo", "bar" -> "bar", "baz" -> "baz")
    }
  }
}
