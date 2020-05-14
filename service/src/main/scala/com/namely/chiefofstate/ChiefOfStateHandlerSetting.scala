package com.namely.chiefofstate

import com.typesafe.config.Config
import com.typesafe.config.ConfigException

/**
 * hold the chief of state handler service setting set in  the configuration.
 * This class need to be kick started ob boot. When the configuration variables are not
 * an exception should be thrown forcing the implementor to set the appropriate value
 */
case class ChiefOfStateHandlerSetting(stateProtoFQN: String, eventProtosFQNs: Seq[String])

object ChiefOfStateHandlerSetting {

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
  def apply(config: Config): ChiefOfStateHandlerSetting = {

    val stateProto: String = config
      .getString("chief-of-state.handlers-settings.state-proto")
      .trim

    val eventProtos: Seq[String] = config
      .getString("chief-of-state.handlers-settings.events-protos")
      .split(",")
      .toSeq
      .map(_.trim)

    if (stateProto.isEmpty || eventProtos.isEmpty)
      throw new RuntimeException("[ChiefOfState] handler service settings not properly set.")

    new ChiefOfStateHandlerSetting(stateProto, eventProtos)
  }
}
