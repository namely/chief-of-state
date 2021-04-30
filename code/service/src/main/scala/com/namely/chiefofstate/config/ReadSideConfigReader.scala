/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.config

import org.slf4j.{ Logger, LoggerFactory }

object ReadSideConfigReader {
  val READ_SIDE_HOST_KEY: String = "HOST"
  val READ_SIDE_PORT_KEY: String = "PORT"
  val READ_SIDE_TLS_KEY: String = "USE_TLS"

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  /**
   * Extracts read side configurations from environment variables
   *
   * @return Seq[GrpcReadSideSetting]
   */
  def getReadSideSettings: Seq[ReadSideConfig] = {
    // let us read the env vars
    val envVars: Map[String, String] = sys.env.filter(pair => {
      val (key, _) = pair
      key.startsWith("COS_READ_SIDE_CONFIG__")
    })

    if (envVars.isEmpty) {
      logger.warn("read sides are enabled but none are configured")
    }

    if (envVars.keySet.exists(v => v.split("__").length != 3)) {
      throw new RuntimeException("One or more of the read side configurations is invalid")
    }

    val groupedEnvVars: Map[String, Iterable[(String, String)]] = envVars.groupMap(_._1.split("__").last)({
      case (k, v) =>
        val settingName: String = k.split("__").tail.head
        require(settingName != "", s"Setting must be defined in $k")

        settingName -> v
    })

    groupedEnvVars
      .map({ case (processorId, settings) =>
        val readSideConfig: ReadSideConfig =
          settings.foldLeft(ReadSideConfig(processorId))({

            case (config, (READ_SIDE_HOST_KEY, value)) =>
              config.copy(host = value)

            case (config, (READ_SIDE_PORT_KEY, value)) =>
              config.copy(port = value.toInt)

            case (config, (READ_SIDE_TLS_KEY, value)) =>
              config.copy(useTls = value.toBooleanOption.getOrElse(false))

            case (config, (key, value)) =>
              config.addSetting(key, value)
          })

        // Requires Host and Port to be defined per GrpcReadSideSetting
        require(readSideConfig.host.nonEmpty, s"ProcessorId $processorId is missing a HOST")
        require(readSideConfig.port > 0, s"ProcessorId $processorId is missing a PORT")

        logger.info(
          s"Configuring read side '$processorId', host=${readSideConfig.host}, port=${readSideConfig.port}, useTls=${readSideConfig.useTls}")

        readSideConfig
      })
      .toSeq
  }
}
