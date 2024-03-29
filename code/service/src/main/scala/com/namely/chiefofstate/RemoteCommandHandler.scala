/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import com.namely.chiefofstate.config.GrpcConfig
import com.namely.protobuf.chiefofstate.v1.common.Header.Value
import com.namely.protobuf.chiefofstate.v1.internal.RemoteCommand
import com.namely.protobuf.chiefofstate.v1.persistence.StateWrapper
import com.namely.protobuf.chiefofstate.v1.writeside.WriteSideHandlerServiceGrpc.WriteSideHandlerServiceBlockingStub
import com.namely.protobuf.chiefofstate.v1.writeside.{ HandleCommandRequest, HandleCommandResponse }
import io.grpc.Metadata
import io.grpc.stub.MetadataUtils
import org.slf4j.{ Logger, LoggerFactory }

import java.util.concurrent.TimeUnit
import scala.util.Try

/**
 * handles command via a gRPC call
 *
 * @param grpcConfig the grpc config
 * @param writeHandlerServicetub the grpc client stub
 */
case class RemoteCommandHandler(grpcConfig: GrpcConfig, writeHandlerServicetub: WriteSideHandlerServiceBlockingStub) {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * handles the given command and return an eventual response
   *
   * @param remoteCommand the command to handle
   * @param priorState the aggregate state before the command to handle
   * @return an eventual HandleCommandResponse
   */
  def handleCommand(remoteCommand: RemoteCommand, priorState: StateWrapper): Try[HandleCommandResponse] = {
    log.debug(s"sending request to the command handler, ${remoteCommand.getCommand.typeUrl}")

    // let us set the client request headers
    val headers: Metadata = new Metadata()

    Try {
      remoteCommand.propagatedHeaders.foreach(header => {
        header.value match {
          case Value.StringValue(value) =>
            headers.put(Metadata.Key.of(header.key, Metadata.ASCII_STRING_MARSHALLER), value)
          case Value.BytesValue(value) =>
            headers.put(Metadata.Key.of(header.key, Metadata.BINARY_BYTE_MARSHALLER), value.toByteArray)
          case Value.Empty =>
            throw new RuntimeException("header value must be string or bytes")
        }
      })

      MetadataUtils
        .attachHeaders(writeHandlerServicetub, headers)
        .withDeadlineAfter(grpcConfig.client.timeout, TimeUnit.MILLISECONDS)
        .handleCommand(
          HandleCommandRequest()
            .withPriorState(priorState.getState)
            .withCommand(remoteCommand.getCommand)
            .withPriorEventMeta(priorState.getMeta))
    }
  }
}
