package com.namely.chiefofstate

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import io.superflat.lagompb.testkit.LagompbSpec
import scala.util.Try

class ChiefOfStateEncryptionSettingSpec extends LagompbSpec {

  "Chief-Of-State encryption settings" should {

    "fail to load settings due to missing values" in {
      val config: Config = ConfigFactory.empty()
      an[RuntimeException] shouldBe thrownBy(ChiefOfStateEncryptionSetting(config))
    }

    "fail to load settings due to unknown encryption class" in {
      val config: Config = ConfigFactory.empty()
        .withValue(
          ChiefOfStateEncryptionSetting.SETTING_KEY,
          ConfigValueFactory.fromAnyRef("not.an.encryptor")
        )
      an[RuntimeException] shouldBe thrownBy(ChiefOfStateEncryptionSetting(config))
    }

    "read the provided encryption class" in {
      val config: Config = ConfigFactory.empty()
        .withValue(
          ChiefOfStateEncryptionSetting.SETTING_KEY,
          ConfigValueFactory.fromAnyRef("io.superflat.lagompb.encryption.NoEncryption")
        )

      val actual: Try[ChiefOfStateEncryptionSetting] = Try(ChiefOfStateEncryptionSetting(config))

      actual.map(_.encryption).toOption shouldBe(Some(io.superflat.lagompb.encryption.NoEncryption))
    }
  }
}
