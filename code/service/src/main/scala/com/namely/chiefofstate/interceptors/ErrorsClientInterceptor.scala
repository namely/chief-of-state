package com.namely.chiefofstate.interceptors

import io.opentracing.Tracer
import io.grpc.ClientInterceptor
import io.grpc.{CallOptions, Channel, ClientCall, MethodDescriptor}
import org.slf4j.{Logger, LoggerFactory}
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall
import io.grpc.Status
import io.grpc.Metadata
import io.grpc.ClientCall.Listener
import io.opentracing.Span

class ErrorsClientInterceptor(tracer: Tracer) extends ClientInterceptor {
  override def interceptCall[T, U](method: MethodDescriptor[T, U],
                                   callOptions: CallOptions,
                                   next: Channel
  ): ClientCall[T, U] = {
    import ErrorsClientInterceptor.logger

    new ErrorsClientInterceptor.CustomClientCall[T, U](next.newCall(method, callOptions), tracer)
  }
}

object ErrorsClientInterceptor {
  final val logger: Logger = LoggerFactory.getLogger(getClass)

  class CustomClientCall[T, U](call: ClientCall[T, U], tracer: Tracer) extends SimpleForwardingClientCall[T, U](call) {
    override def start(responseListener: Listener[U], headers: Metadata): Unit = {
      super.start(new CustomListener[U](tracer, responseListener), headers)
    }
  }

  class CustomListener[U](tracer: Tracer, listener: ClientCall.Listener[U])
      extends SimpleForwardingClientCallListener[U](listener) {

    val span: Option[Span] = Option(tracer.activeSpan())

    override def onClose(status: Status, trailers: Metadata): Unit = {
      if (!status.isOk()) {
        logger.debug(s"${status.isOk()} ${status.getCode()} ${status.getDescription()}")
        OpentracingHelpers.reportErrorToSpan(span, status.asException())
      }
      super.onClose(status, trailers)
    }
  }
}
