package com.namely.chiefofstate

import com.typesafe.config.{Config, ConfigException}

/**
 * hold the chief of state handler service setting set in  the configuration.
 * This class need to be kick started on boot. When the configuration variables are not set
 * an exception should be thrown forcing the implementor to set the appropriate value
 */
case class HandlerSetting(stateFQNs: Seq[String], eventFQNs: Seq[String])

object HandlerSetting {

  /**
   * Help build the [[com.namely.chiefofstate.HandlerSetting]]
   * This code will break if the env variable are not properly set which will halt the
   * application bootstrap.
   *
   * @param config application configuration
   * @return
   */
  @throws(classOf[ConfigException])
  def apply(config: Config): HandlerSetting = {

    val stateProtos: Seq[String] = config
      .getString("chief-of-state.handlers-settings.states-proto")
      .split(",")
      .toSeq
      .map(_.trim)
      .filter(_.nonEmpty)

    val eventProtos: Seq[String] = config
      .getString("chief-of-state.handlers-settings.events-protos")
      .split(",")
      .toSeq
      .map(_.trim)
      .filter(_.nonEmpty)

    if (stateProtos.isEmpty || eventProtos.isEmpty)
      throw new RuntimeException("[ChiefOfState] handler service settings not properly set.")

    new HandlerSetting(stateProtos, eventProtos)
  }
}
