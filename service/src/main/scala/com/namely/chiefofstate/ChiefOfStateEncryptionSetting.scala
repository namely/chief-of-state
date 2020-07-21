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
    * constant key for the encryptor setting
    */
  val SETTING_KEY: String = "chief-of-state.encryption.encryption-class"

  /**
   * Companion for [[com.namely.chiefofstate.ChiefOfStateEncryptionSetting]]
   * class which reads the relevant `chief-of-state.encryption` configurations
   * and or halts the application bootstrap on failure.
   *
   * @param config application configuration
   * @throws com.typesafe.config.ConfigException
   * @return
   */
  @throws(classOf[ConfigException])
  def apply(config: Config): ChiefOfStateEncryptionSetting = {

    // read the preferred encryptor class from config
    val encryptionClassName: String = config
      .getString(SETTING_KEY)
      .trim

    if (encryptionClassName.isEmpty()) {
      throw new RuntimeException("[ChiefOfState] encryption settings not properly set")
    }

    // attempt reflection
    val output: Try[ProtoEncryption] = Try {
      val clazz: Class[_ <: Any] = Class.forName(encryptionClassName)
      val runtimeMirror: universe.Mirror = universe.runtimeMirror(clazz.getClassLoader)
      val module: universe.ModuleSymbol = runtimeMirror.staticModule(clazz.getName)
      runtimeMirror.reflectModule(module).instance.asInstanceOf[ProtoEncryption]
    }

    output match {
      case Success(protoEncryption) =>
        ChiefOfStateEncryptionSetting(protoEncryption)

      case Failure(e) =>
        throw new RuntimeException(s"[ChiefOfState] could not load ProtoEncryption '$encryptionClassName'.")
    }
  }
}
