package com.namely.chiefofstate

import io.grpc.ManagedChannel
import io.grpc.netty.NegotiationType
import io.grpc.netty.NegotiationType.{PLAINTEXT, TLS}
import io.grpc.netty.NettyChannelBuilder

object NettyHelper {
  def buildChannel(host: String, port: Int, useTls: Boolean): ManagedChannel = {

    // decide on negotiation type
    val negotiationType: NegotiationType = if (useTls) TLS else PLAINTEXT

    NettyChannelBuilder
      .forAddress(host, port)
      .negotiationType(negotiationType)
      .build()
  }
}
