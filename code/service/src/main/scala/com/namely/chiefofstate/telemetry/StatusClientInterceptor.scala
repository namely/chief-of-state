/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.telemetry

import io.grpc.ForwardingClientCall.SimpleForwardingClientCall
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener
import io.grpc._
import io.opentelemetry.api.trace.Span

/**
 * gRPC client interceptor that adds the returned gRPC status code to the trace attributes
 */
class StatusClientInterceptor extends ClientInterceptor {
  override def interceptCall[ReqT, RespT](method: MethodDescriptor[ReqT, RespT],
                                          callOptions: CallOptions,
                                          next: Channel
  ): ClientCall[ReqT, RespT] = {
    new StatusClientInterceptor.CustomClientCall(next.newCall(method, callOptions))
  }
}

object StatusClientInterceptor {
  val GrpcStatusLabel = "grpc.status_code"

  val GrpcOkLabel = "grpc.isOk"

  val GrpcKindLabel = "grpc.kind"

  val GrpcKind = "client"

  /**
   * Custom call wrapper that will hook in our custom listener to log returned status codes
   * @param call client call to be wrapped
   */
  class CustomClientCall[T, U](call: ClientCall[T, U]) extends SimpleForwardingClientCall[T, U](call) {
    override def start(responseListener: ClientCall.Listener[U], headers: Metadata): Unit = {
      super.start(new CustomListener(responseListener), headers)
    }
  }

  class CustomListener[U](listener: ClientCall.Listener[U]) extends SimpleForwardingClientCallListener[U](listener) {

    /**
     * Custom close logic that reports the status code
     * @param status gRPC status code for the closed call
     * @param trailers metadata used in the call
     */
    override def onClose(status: Status, trailers: Metadata): Unit = {
      Span
        .current()
        .setAttribute(GrpcStatusLabel, status.getCode.name())
        .setAttribute(GrpcKindLabel, GrpcKind)
        .setAttribute(GrpcOkLabel, status.equals(Status.OK).toString)
      super.onClose(status, trailers)
    }
  }
}
