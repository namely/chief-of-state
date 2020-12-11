/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.telemetry

import io.grpc._
import io.grpc.ClientCall.Listener
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener
import io.opentracing.{Span, Tracer}
import org.slf4j.{Logger, LoggerFactory}

/**
 * gRPC client intercpetor for ensuring remote errors are logged
 * on the current span
 *
 * @param tracer the tracer, likely the GlobalGracer
 */
class ErrorsClientInterceptor(tracer: Tracer) extends ClientInterceptor {

  /**
   * intercept the call and apply the custom listener
   *
   * @param method the method to intercept
   * @param callOptions the call options
   * @param next the channel
   * @return a client call wrapped in the listener
   */
  override def interceptCall[T, U](
    method: MethodDescriptor[T, U],
    callOptions: CallOptions,
    next: Channel
  ): ClientCall[T, U] = {

    new ErrorsClientInterceptor.CustomClientCall[T, U](next.newCall(method, callOptions), tracer)
  }
}

/**
 * companion object with the custom client call and listener classes
 */
object ErrorsClientInterceptor {
  final val logger: Logger = LoggerFactory.getLogger(getClass)

  /**
   * custom client call class for applying the custom listener
   *
   * @param call a call to intercept
   * @param tracer the tracer to use
   */
  class CustomClientCall[T, U](call: ClientCall[T, U], tracer: Tracer) extends SimpleForwardingClientCall[T, U](call) {

    /**
     * on start of a call, apply a custom reponse listener
     *
     * @param responseListener a custom response listener
     * @param headers the headers of the call
     */
    override def start(responseListener: Listener[U], headers: Metadata): Unit = {
      super.start(new CustomListener[U](tracer, responseListener), headers)
    }
  }

  /**
   * custom listener class for intercepting the call and propagaing
   * non-ok statuses as errors into the tracer
   *
   * @param tracer a tracer to report to
   * @param listener a listener to wrap
   */
  class CustomListener[U](tracer: Tracer, listener: ClientCall.Listener[U])
      extends SimpleForwardingClientCallListener[U](listener) {

    val span: Option[Span] = Option(tracer.activeSpan())

    /**
     * on close of the call, report a non-ok status as an error
     * to the tracer
     *
     * @param status the call status
     * @param trailers trailing metadata
     */
    override def onClose(status: Status, trailers: Metadata): Unit = {
      if (!status.isOk()) {
        logger.debug(s"${status.isOk()} ${status.getCode()} ${status.getDescription()}")
        OpentracingHelpers.reportErrorToSpan(span, status.asException())
      }
      super.onClose(status, trailers)
    }
  }
}
