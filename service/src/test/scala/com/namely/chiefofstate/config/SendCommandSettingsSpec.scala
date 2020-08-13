package com.namely.chiefofstate.config

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import io.superflat.lagompb.testkit.BaseSpec

import scala.util.{Success, Try}

class SendCommandSettingsSpec extends BaseSpec {

  "Chief-Of-State send-command settings" should {

    "fail to load settings due to missing setting key" in {
      an[RuntimeException] shouldBe thrownBy(
        SendCommandSettings(
          ConfigFactory
            .empty()
            .withValue(
              SendCommandSettings.PERSISTED_HEADERS_KEY,
              ConfigValueFactory.fromAnyRef("x")
            )
        )
      )

      an[RuntimeException] shouldBe thrownBy(
        SendCommandSettings(
          ConfigFactory
            .empty()
            .withValue(
              SendCommandSettings.PROPAGATED_HEADERS_KEY,
              ConfigValueFactory.fromAnyRef("x")
            )
        )
      )
    }

    "defaults to None when no headers set" in {
      val config: Config = ConfigFactory
        .empty()
        .withValue(SendCommandSettings.PERSISTED_HEADERS_KEY, ConfigValueFactory.fromAnyRef(""))
        .withValue(SendCommandSettings.PROPAGATED_HEADERS_KEY, ConfigValueFactory.fromAnyRef(""))

      SendCommandSettings(config) shouldBe (SendCommandSettings(propagatedHeaders = Set(), persistedHeaders = Set()))
    }

    "parse csv string" in {
      val config: Config = ConfigFactory
        .empty()
        .withValue(
          SendCommandSettings.PERSISTED_HEADERS_KEY,
          ConfigValueFactory.fromAnyRef("foo,bar,baz")
        )

      val actual: Try[Set[String]] = Try(
        SendCommandSettings
          .getCsvSetting(
            config,
            SendCommandSettings.PERSISTED_HEADERS_KEY
          )
      )

      actual shouldBe Success(Set("foo", "bar", "baz"))
    }
  }
}
