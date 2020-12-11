/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.config

import com.typesafe.config.Config

/**
 * GrpcConfig reads the gRPC settings from the config
 *
 * @param client the grpc client general settings
 * @param server the grpc server setting
 */
case class GrpcConfig(client: GrpcClient, server: GrpcServer)

case class GrpcClient(timeout: Int)

case class GrpcServer(host: String, port: Int)

object GrpcConfig {

  /**
   * creates a new instance of rhe GrpcConfig
   *
   * @param config the configuration object
   * @return a new instance of GrpcConfig
   */
  def apply(config: Config): GrpcConfig = {
    GrpcConfig(
      GrpcClient(config.getInt("chiefofstate.grpc.client.deadline-timeout")),
      GrpcServer(
        config.getString("chiefofstate.grpc.server.address"),
        config.getInt("chiefofstate.grpc.server.port")
      )
    )
  }
}
