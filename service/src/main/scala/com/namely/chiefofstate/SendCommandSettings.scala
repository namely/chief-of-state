package com.namely.chiefofstate

import com.typesafe.config.Config

/**
 * Contains the configurations for COS send command
 *
 * @param propagatedHeaders headers to pass to the write side handler
 * @param persistedHeaders: headers to persist to the journal
 */
case class SendCommandSettings(
  propagatedHeaders: Set[String],
  persistedHeaders: Set[String]
)

/**
 * Companion for SendCommandSettings
 */
object SendCommandSettings {
  // setting key for propagated headers
  val PROPAGATED_HEADERS_KEY: String = "chief-of-state.send-command.propagated-headers"
  // setting key for persisted headers
  val PERSISTED_HEADERS_KEY: String = "chief-of-state.send-command.persisted-headers"

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
      propagatedHeaders = getCsvSetting(config, PROPAGATED_HEADERS_KEY),
      persistedHeaders = getCsvSetting(config, PERSISTED_HEADERS_KEY)
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
