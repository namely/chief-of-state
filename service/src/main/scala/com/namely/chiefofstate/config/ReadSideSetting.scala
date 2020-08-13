package com.namely.chiefofstate.config

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings

/**
 * GRPC Read Side Configurations
 *
 * @param processorId Unique Id for the processor
 * @param host Host
 * @param port Port
 */
final case class ReadSideSetting(
  processorId: String,
  host: Option[String] = None,
  port: Option[Int] = None,
  settings: Map[String, String] = Map()
) {

  /**
   * Adds a setting to the config
   *
   * @param key Setting key
   * @param value Setting value
   */
  def addSetting(key: String, value: String): ReadSideSetting = copy(settings = settings + (key -> value))

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
  def removeSetting(key: String): ReadSideSetting = copy(settings = settings.removed(key))

  /**
   * Lists the settings from the config
   *
   * @return Map[String, String]
   */
  def listSettings: Map[String, String] = settings

  // TODO: Add handlers for misc GRPC settings
  // TODO: Add spec w/ a fake ActorSystem
  /**
   * Constructs the GrpcClientSettings
   *
   * @param actorSystem ActorSystem instance
   * @return GrpcClientSettings
   */
  def getGrpcClientSettings(implicit actorSystem: ActorSystem): GrpcClientSettings = {
    require(host.isDefined, "Must define a host in the GrpcReadSideSetting")
    require(port.isDefined, "Must define a port in the GrpcReadSideSetting")

    GrpcClientSettings.connectToServiceAt(host.get, port.get)
  }
}

object ReadSideSetting {

  val READ_SIDE_HOST_KEY: String = "HOST"
  val READ_SIDE_PORT_KEY: String = "PORT"

  /**
   * Extracts read side configurations from environment variables
   *
   * @return Seq[GrpcReadSideSetting]
   */
  def getReadSideSettings: Seq[ReadSideSetting] = {

    val envVars: Map[String, String] = sys.env
      .filter(_._1.startsWith("COS_READ_SIDE_CONFIG__"))

    if (envVars.exists(_._1.split("__").length != 3)) {
      throw new Exception("One or more of the read side configurations is invalid")
    }

    val groupedEnvVars: Map[String, Iterable[(String, String)]] = envVars
      .groupMap(_._1.split("__").last)({
        case (k, v) =>
          val settingName: String = k.split("__").tail.head
          require(settingName != "", s"Setting must be defined in $k")

          settingName -> v
      })

    groupedEnvVars
      .map({
        case (processorId, settings) =>
          val grpcConfig: ReadSideSetting = settings.foldLeft(ReadSideSetting(processorId))({
            case (config, (key, value)) =>
              if (key == READ_SIDE_HOST_KEY) {
                config.copy(host = Some(value))
              } else if (key == READ_SIDE_PORT_KEY) {
                config.copy(port = Some(value).map(_.toInt))
              } else {
                config.addSetting(key, value)
              }
          })

          // Requires Host and Port to be defined per GrpcReadSideSetting
          require(grpcConfig.host.isDefined, s"ProcessorId $processorId is missing a HOST")
          require(grpcConfig.port.isDefined, s"ProcessorId $processorId is missing a PORT")

          grpcConfig
      })
      .toSeq
  }
}
