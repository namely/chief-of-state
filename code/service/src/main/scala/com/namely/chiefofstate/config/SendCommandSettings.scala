package com.namely.chiefofstate.config

import com.typesafe.config.Config

/**
 * Contains the configurations for COS send command
 *
 * @param propagatedHeaders headers to pass to the write side handler
 */
case class SendCommandSettings(
  propagatedHeaders: Set[String]
)

/**
 * Companion for SendCommandSettings
 */
object SendCommandSettings {
  // setting key for propagated headers
  val PROPAGATED_HEADERS_KEY: String = "chief-of-state.send-command.propagated-headers"

  /**
   * Companion constructor for [[com.namely.chiefofstate.SendCommandSettings]]
   * class which reads the relevant `chief-of-state.sendcommand` configurations
   * and or halts the application bootstrap on failure.
   *
   * @param config application configuration
   * @return
   */
  def apply(config: Config): SendCommandSettings = {
    SendCommandSettings(
      propagatedHeaders = getCsvSetting(config, PROPAGATED_HEADERS_KEY)
    )
  }

  /**
   * returns sequence of csv strings from the config
   *
   * @param config a config
   * @param key the path to query
   * @return set of strings
   */
  def getCsvSetting(config: Config, key: String): Set[String] = {
    config
      .getString(key)
      .trim
      .split(",")
      .filter(_.nonEmpty)
      .toSet
  }
}
