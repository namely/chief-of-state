package com.namely.chiefofstate.config

import com.namely.chiefofstate.test.helpers.TestSpec
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}

import scala.util.{Success, Try}

class SendCommandSettingsSpec extends TestSpec {

  "Chief-Of-State send-command settings" should {

    "fail to load settings due to missing setting key" in {
      an[RuntimeException] shouldBe thrownBy(SendCommandSettings(ConfigFactory.empty()))
    }

    "defaults to None when no headers set" in {
      val config: Config = ConfigFactory
        .empty()
        .withValue(SendCommandSettings.PROPAGATED_HEADERS_KEY, ConfigValueFactory.fromAnyRef(""))

      SendCommandSettings(config) shouldBe SendCommandSettings(propagatedHeaders = Set())
    }

    "parse csv string" in {
      val config: Config = ConfigFactory
        .empty()
        .withValue(
          SendCommandSettings.PROPAGATED_HEADERS_KEY,
          ConfigValueFactory.fromAnyRef("foo,bar,baz")
        )

      val actual: Try[Set[String]] = Try(
        SendCommandSettings
          .getCsvSetting(
            config,
            SendCommandSettings.PROPAGATED_HEADERS_KEY
          )
      )

      actual shouldBe Success(Set("foo", "bar", "baz"))
    }
  }
}
