package com.namely.chiefofstate.config

import com.typesafe.config.Config

/**
 * WriteHandler configuration
 *
 * @param host the gRPC host
 * @param port the gRPC port
 */
case class WriteSideConfig(
  host: String,
  port: Int
)

object WriteSideConfig {

  private val hostKey = "chiefofstate.write-side.host"
  private val portKey = "chiefofstate.write-side.port"

  /**
   * creates an instancee of WriteSideConfig
   *
   * @param config the configuration object
   * @return a new instance of WriteSideConfig
   */
  def apply(config: Config): WriteSideConfig = {

    WriteSideConfig(
      config.getString(hostKey),
      config.getInt(portKey)
    )
  }
}
