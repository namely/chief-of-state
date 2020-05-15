package com.namely.chiefofstate

import com.namely.lagom.testkit.NamelyTestSpec
import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory

class ChiefOfStateHandlerSettingSpec extends NamelyTestSpec {

  "Chief-Of-State handler settings" should {

    "fail to load settings because env variable are not set" in {

      an[ConfigException.UnresolvedSubstitution] shouldBe thrownBy {
        val config: Config = ConfigFactory.load()
        ChiefOfStateHandlerSetting(config)
      }
    }

    "fail to load settings because env variables not set properly" in {
      val config: Config = ConfigFactory.parseResources("handler-settings.conf").resolve()
      an[RuntimeException] shouldBe thrownBy(
        ChiefOfStateHandlerSetting(config)
      )
    }

    "fail to load settings because env variables for events-protos not set" in {
      val config: Config = ConfigFactory
        .parseResources("handler-settings.conf")
        .withValue(
          "chief-of-state.handlers-settings.state-proto",
          ConfigValueFactory.fromAnyRef("namely.org_units.OrgUnit")
        )
        .resolve()
      an[RuntimeException] shouldBe thrownBy(
        ChiefOfStateHandlerSetting(config)
      )
    }

    "succeed to load settings because env variables for events-protos not set" in {
      val config: Config = ConfigFactory
        .parseResources("handler-settings.conf")
        .withValue(
          "chief-of-state.handlers-settings.state-proto",
          ConfigValueFactory.fromAnyRef("namely.org_units.OrgUnit")
        )
        .withValue(
          "chief-of-state.handlers-settings.events-protos",
          ConfigValueFactory.fromAnyRef("namely.org_units.OrgUnitTypeCreated", "namely.org_units.OrgUnitTypeUpdated")
        )
        .resolve()
      noException shouldBe thrownBy(
        ChiefOfStateHandlerSetting(config)
      )
    }
  }
}
