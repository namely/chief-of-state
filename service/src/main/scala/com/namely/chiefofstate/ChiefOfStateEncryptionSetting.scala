package com.namely.chiefofstate

import com.typesafe.config.{Config, ConfigException}
import io.superflat.lagompb.encryption.ProtoEncryption

import scala.reflect.runtime.universe
import scala.util.{Try, Success, Failure}

/**
  * Contains the configurations for COS encryption
  *
  * @param encryption
  */
case class ChiefOfStateEncryptionSetting(
  encryption: ProtoEncryption,
)

object ChiefOfStateEncryptionSetting {

  /**
   * Help build the [[com.namely.chiefofstate.ChiefOfStateHandlerSetting]]
   * This code will break if the env variable are not properly set which will halt the
   * application bootstrap.
   *
   * @param config application configuration
   * @throws com.typesafe.config.ConfigException
   * @return
   */
  @throws(classOf[ConfigException])
  def apply(config: Config): ChiefOfStateEncryptionSetting = {

    val encryptionClassName: String = config
      .getString("chief-of-state.encryption.encryption-class")
      .trim

    val output: Try[ProtoEncryption] = Try {
      Class.forName(encryptionClassName).asInstanceOf[ProtoEncryption]
    }

    output match {
      case Success(protoEncryption) =>
        ChiefOfStateEncryptionSetting(protoEncryption)

      case Failure(e) =>
        throw new RuntimeException("[ChiefOfState] handler service settings not properly set.")
    }
  }
}
