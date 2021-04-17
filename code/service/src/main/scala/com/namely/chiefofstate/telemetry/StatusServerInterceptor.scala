/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.telemetry

import io.grpc._
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall
import io.opentelemetry.api.trace.Span

/**
 * gRPC server interceptor that adds the gRPC status codes details to the traces
 */
class StatusServerInterceptor extends ServerInterceptor {
  override def interceptCall[ReqT, RespT](
      call: ServerCall[ReqT, RespT],
      headers: Metadata,
      next: ServerCallHandler[ReqT, RespT]): ServerCall.Listener[ReqT] = {
    next.startCall(new StatusServerInterceptor.CustomServerCall(call), headers)
  }
}

object StatusServerInterceptor {
  val GrpcStatusLabel = "grpc.status_code"

  val GrpcOkLabel = "grpc.ok"

  val GrpcKindLabel = "grpc.kind"

  val GrpcKind = "server"

  /**
   * Custom server call wrapper that reports the gRPC status on close of call
   *
   * @param call server call to be wrapped
   */
  class CustomServerCall[T, U](call: ServerCall[T, U]) extends SimpleForwardingServerCall[T, U](call) {

    /**
     * Custom close logic that reports the status code
     * @param status gRPC status code for the closed call
     * @param trailers metadata used in the call
     */
    override def close(status: Status, trailers: Metadata): Unit = {
      Span
        .current()
        .setAttribute(GrpcStatusLabel, status.getCode.name())
        .setAttribute(GrpcKindLabel, GrpcKind)
        .setAttribute(GrpcOkLabel, status.equals(Status.OK).toString)
      super.close(status, trailers)
    }
  }
}
