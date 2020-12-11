/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import io.grpc.netty.{NegotiationType, NettyChannelBuilder}
import io.grpc.netty.NegotiationType.{PLAINTEXT, TLS}

object NettyHelper {

  /**
   * returns a NettyChannelBuilder
   *
   * @param host host to connect to
   * @param port port to use
   * @param useTls true/false to enable TLS
   * @return a NettyChannelBuilder
   */
  def builder(host: String, port: Int, useTls: Boolean): NettyChannelBuilder = {

    // decide on negotiation type
    val negotiationType: NegotiationType = if (useTls) TLS else PLAINTEXT

    NettyChannelBuilder
      .forAddress(host, port)
      .negotiationType(negotiationType)
  }
}
