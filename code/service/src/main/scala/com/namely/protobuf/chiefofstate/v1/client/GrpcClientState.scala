package com.namely.protobuf.chiefofstate.v1.client

import akka.event.LoggingAdapter
import akka.grpc.internal.{ChannelUtils, InternalChannel, NettyClientUtils}
import akka.grpc.GrpcClientSettings
import akka.Done

import scala.concurrent.{ExecutionContextExecutor, Future}

class GrpcClientState(val internalChannel: InternalChannel)(implicit ec: ExecutionContextExecutor) {
  def this(settings: GrpcClientSettings, log: LoggingAdapter)(implicit ec: ExecutionContextExecutor) =
    this(NettyClientUtils.createChannel(settings, log)(ec))

  def closed(): Future[Done] = internalChannel.done

  def close(): Future[Done] = ChannelUtils.close(internalChannel)
}
