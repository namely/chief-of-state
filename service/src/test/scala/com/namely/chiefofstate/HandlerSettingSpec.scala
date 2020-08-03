package com.namely.chiefofstate

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import io.superflat.lagompb.testkit.BaseSpec

class HandlerSettingSpec extends BaseSpec {

  "Chief-Of-State handler settings" should {

    "fail to load settings because env variables not set properly" in {
      val config: Config = ConfigFactory.parseResources("handler-settings.conf").resolve()
      an[RuntimeException] shouldBe thrownBy(HandlerSetting(config))
    }

    "fail to load settings because env variables for events-protos not set" in {
      val config: Config = ConfigFactory
        .parseResources("handler-settings.conf")
        .withValue(
          "chief-of-state.handlers-settings.states-proto",
          ConfigValueFactory.fromAnyRef("namely.org_units.OrgUnit")
        )
        .resolve()
      an[RuntimeException] shouldBe thrownBy(HandlerSetting(config))
    }

    "succeed to load settings because env variables for events-protos not set" in {
      val config: Config = ConfigFactory
        .parseResources("handler-settings.conf")
        .withValue(
          "chief-of-state.handlers-settings.states-proto",
          ConfigValueFactory.fromAnyRef("namely.org_units.OrgUnit")
        )
        .withValue(
          "chief-of-state.handlers-settings.events-protos",
          ConfigValueFactory.fromAnyRef("namely.org_units.OrgUnitTypeCreated", "namely.org_units.OrgUnitTypeUpdated")
        )
        .resolve()
      noException shouldBe thrownBy(HandlerSetting(config))
    }
  }
}
