package com.namely.chiefofstate

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import io.superflat.lagompb.testkit.LagompbSpec

import scala.util.Try

class EncryptionSettingSpec extends LagompbSpec {

  "Chief-Of-State encryption settings" should {

    "fail to load settings due to missing values" in {
      val config: Config = ConfigFactory.empty()
      an[RuntimeException] shouldBe thrownBy(EncryptionSetting(config))
    }

    "fail to load settings due to unknown encryption class" in {
      val config: Config = ConfigFactory
        .empty()
        .withValue(EncryptionSetting.SETTING_KEY, ConfigValueFactory.fromAnyRef("not.an.encryptor"))
      an[RuntimeException] shouldBe thrownBy(EncryptionSetting(config))
    }

    "read the provided encryption class" in {
      val config: Config = ConfigFactory
        .empty()
        .withValue(
          EncryptionSetting.SETTING_KEY,
          ConfigValueFactory.fromAnyRef("io.superflat.lagompb.encryption.NoEncryption")
        )

      val actual: Try[EncryptionSetting] = Try(EncryptionSetting(config))

      actual.map(_.encryption).toOption shouldBe (Some(io.superflat.lagompb.encryption.NoEncryption))
    }
  }
}
