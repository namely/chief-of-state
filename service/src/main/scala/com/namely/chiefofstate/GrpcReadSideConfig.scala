package com.namely.chiefofstate

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings

import scala.collection.mutable

/**
 * GRPC Read Side Configurations
 *
 * @param processorId Unique Id for the processor
 * @param host Host
 * @param port Port
 */
final case class GrpcReadSideConfig(processorId: String, host: Option[String] = None, port: Option[Int] = None) {

  private val settings: mutable.Map[String, String] = mutable.Map()

  /**
   * Adds a setting to the config
   *
   * @param key Setting key
   * @param value Setting value
   */
  def addSetting(key: String, value: String): Unit = settings += (key -> value)

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
  def removeSetting(key: String): Boolean = settings.remove(key).isDefined

  /**
   * Lists the settings from the config
   *
   * @return Map[String, String]
   */
  def listSettings: Map[String, String] = settings.toMap

  // TODO: Add handlers for misc GRPC settings
  // TODO: Add spec w/ a fake ActorSystem
  /**
   * Constructs the GrpcClientSettings
   *
   * @param actorSystem ActorSystem instance
   * @return GrpcClientSettings
   */
  def getGrpcClientSettings(implicit actorSystem: ActorSystem): GrpcClientSettings = {
    require(host.isDefined, "Must define a host in the GrpcReadSideConfig")
    require(port.isDefined, "Must define a port in the GrpcReadSideConfig")

    GrpcClientSettings.connectToServiceAt(
      host.get,
      port.get
    )
  }
}
