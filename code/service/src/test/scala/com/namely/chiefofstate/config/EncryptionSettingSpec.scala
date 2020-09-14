package com.namely.chiefofstate.config

import com.namely.chiefofstate.test.helpers.TestSpec
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import io.superflat.lagompb.encryption.NoEncryption

import scala.util.{Success, Try}

class EncryptionSettingSpec extends TestSpec {

  "Chief-Of-State encryption settings" should {

    "fail to load settings due to missing setting key" in {
      val config: Config = ConfigFactory.empty()
      an[RuntimeException] shouldBe thrownBy(EncryptionSetting(config))
    }

    "defaults to None when no encryption value set" in {
      val config: Config = ConfigFactory
        .empty()
        .withValue(EncryptionSetting.SETTING_KEY, ConfigValueFactory.fromAnyRef(""))

      EncryptionSetting(config) shouldBe (EncryptionSetting(encryption = None))
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

      actual shouldBe Success(EncryptionSetting(Some(NoEncryption)))
    }
  }
}
