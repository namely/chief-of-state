package com.namely.chiefofstate.config

import com.typesafe.config.{Config, ConfigException}
import io.superflat.lagompb.encryption.ProtoEncryption

import scala.reflect.runtime.universe
import scala.util.{Failure, Success, Try}

/**
 * Contains the configurations for COS encryption
 *
 * @param encryption encryptor
 */
case class EncryptionSetting(encryption: Option[ProtoEncryption])

object EncryptionSetting {

  /**
   * constant key for the encryptor setting
   */
  val SETTING_KEY: String = "chief-of-state.encryption.encryption-class"

  /**
   * Companion for [[com.namely.chiefofstate.EncryptionSetting]]
   * class which reads the relevant `chief-of-state.encryption` configurations
   * and or halts the application bootstrap on failure.
   *
   * @param config application configuration
   * @return
   */
  @throws(classOf[ConfigException])
  def apply(config: Config): EncryptionSetting = {

    // read the preferred encryptor class from config
    val encryptionClassName: String = config
      .getString(SETTING_KEY)
      .trim

    if (encryptionClassName.isEmpty) {
      EncryptionSetting(encryption = None)
    } else {
      // attempt reflection
      val output: Try[ProtoEncryption] = Try {
        val clazz: Class[_ <: Any] = Class.forName(encryptionClassName)
        val runtimeMirror: universe.Mirror = universe.runtimeMirror(clazz.getClassLoader)
        val module: universe.ModuleSymbol = runtimeMirror.staticModule(clazz.getName)
        runtimeMirror.reflectModule(module).instance.asInstanceOf[ProtoEncryption]
      }

      output match {
        case Success(protoEncryption) =>
          EncryptionSetting(Some(protoEncryption))

        case Failure(e) =>
          throw new RuntimeException(s"[ChiefOfState] could not load ProtoEncryption '$encryptionClassName'.")
      }
    }
  }
}
