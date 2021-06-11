/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.config

final case class ReadSideConfig(
    processorId: String,
    host: String = "",
    port: Int = -1,
    useTls: Boolean = false,
    settings: Map[String, String] = Map.empty[String, String],
    useStreaming: Boolean = false) {

  /**
   * Adds a setting to the config
   *
   * @param key Setting key
   * @param value Setting value
   */
  def addSetting(key: String, value: String): ReadSideConfig = copy(settings = settings + (key -> value))

  /**
   * Gets the setting from the config
   *
   * @param key Setting key
   * @return Setting value
   */
  def getSetting(key: String): Option[String] = settings.get(key)

  /**
   * Removes the setting from the config
   *
   * @param key Setting key
   * @return
   */
  def removeSetting(key: String): ReadSideConfig = copy(settings = settings.removed(key))

  /**
   * Lists the settings from the config
   *
   * @return Map[String, String]
   */
  def listSettings: Map[String, String] = settings
}
