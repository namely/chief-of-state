package com.namely.chiefofstate.config

final case class ReadSideConfig(
  processorId: String,
  host: Option[String] = None,
  port: Option[Int] = None,
  settings: Map[String, String] = Map.empty[String, String]
) {

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
