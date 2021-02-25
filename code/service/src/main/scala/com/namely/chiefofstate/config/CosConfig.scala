/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.config

import akka.util.Timeout
import com.typesafe.config.Config

import scala.concurrent.duration.DurationInt

/**
 * Main config
 *
 * @param serviceName the service name
 * @param askTimeout the timeout needed by the aggreegate to reply when handling a command
 * @param snapshotConfig the snapshot configuration
 * @param eventsConfig the events configuration
 * @param grpcConfig the grpc config
 * @param writeSideConfig the commands/events handler config
 */
final case class CosConfig(
  serviceName: String,
  askTimeout: Timeout,
  snapshotConfig: SnapshotConfig,
  eventsConfig: EventsConfig,
  grpcConfig: GrpcConfig,
  writeSideConfig: WriteSideConfig,
  createDataStores: Boolean,
  enableReadSide: Boolean,
  telemetryConfig: TelemetryConfig
)

object CosConfig {
  private val serviceNameKey: String = "chiefofstate.service-name"
  private val askTimeoutKey: String = "chiefofstate.ask-timeout"
  private val createDataStorekey: String = "chiefofstate.create-stores.auto"
  private val enableReadSideKey: String = "chiefofstate.read-side.enabled"

  /**
   * creates a new CosConfig instance
   *
   * @param config the config object
   * @return the newly created instance
   */
  def apply(
    config: Config
  ): CosConfig = {
    CosConfig(
      config.getString(serviceNameKey),
      Timeout(config.getInt(askTimeoutKey).seconds),
      SnapshotConfig(config),
      EventsConfig(config),
      GrpcConfig(config),
      WriteSideConfig(config),
      config.getBoolean(createDataStorekey),
      config.getBoolean(enableReadSideKey),
      TelemetryConfig(config)
    )
  }
}
