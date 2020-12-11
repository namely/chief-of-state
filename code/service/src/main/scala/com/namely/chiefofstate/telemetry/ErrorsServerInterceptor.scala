/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.telemetry

import io.grpc._
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall
import io.grpc.ServerCall.Listener
import io.opentracing.{Span, Tracer}
import org.slf4j.{Logger, LoggerFactory}

/**
 * custom server gRPC interceptor for propagating errors to the tracer
 *
 * @param tracer the tracer to use, likely GlobalGracer
 */
class ErrorsServerInterceptor(tracer: Tracer) extends ServerInterceptor {

  /**
   * intercepts a call and starts the custom caller
   *
   * @param call a call to intercept
   * @param headers the metadata
   * @param next the call handler
   * @return a listener
   */
  override def interceptCall[T, U](
    call: ServerCall[T, U],
    headers: Metadata,
    next: ServerCallHandler[T, U]
  ): Listener[T] = {

    next.startCall(
      new ErrorsServerInterceptor.CustomCaller[T, U](tracer, call),
      headers
    )

  }
}

object ErrorsServerInterceptor {
  final val logger: Logger = LoggerFactory.getLogger(getClass)

  /**
   * custom caller class for logging errors on close of the call
   *
   * @param tracer the tracer to report to
   * @param call a call to intercept
   */
  class CustomCaller[T, U](tracer: Tracer, call: ServerCall[T, U]) extends SimpleForwardingServerCall[T, U](call) {

    val span: Option[Span] = Option(tracer.activeSpan())

    /**
     * on close of the RPC, report any non-ok statuses
     * as errors to the tracer.
     *
     * @param status the status of the call
     * @param trailers the trailing metadata
     */
    override def close(status: Status, trailers: Metadata): Unit = {
      if (!status.isOk()) {
        OpentracingHelpers.reportErrorToSpan(span, status.asException())
      }
      super.close(status, trailers)
    }
  }
}
