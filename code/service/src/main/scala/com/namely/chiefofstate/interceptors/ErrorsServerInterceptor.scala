package com.namely.chiefofstate.interceptors

import io.grpc.ServerInterceptor
import io.grpc.{Metadata, ServerCall, ServerCallHandler}
import io.grpc.ServerCall.Listener
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall
import io.grpc.Status
import org.slf4j.{Logger, LoggerFactory}
import io.opentracing.Tracer
import io.opentracing.Span

class ErrorsServerInterceptor(tracer: Tracer) extends ServerInterceptor {
  override def interceptCall[T, U](call: ServerCall[T, U],
                                   headers: Metadata,
                                   next: ServerCallHandler[T, U]
  ): Listener[T] = {

    import ErrorsServerInterceptor.logger

    next.startCall(
      new ErrorsServerInterceptor.CustomCaller[T, U](tracer, call),
      headers
    )

  }
}

object ErrorsServerInterceptor {
  final val logger: Logger = LoggerFactory.getLogger(getClass)

  class CustomCaller[T, U](tracer: Tracer, call: ServerCall[T, U]) extends SimpleForwardingServerCall[T, U](call) {

    val span: Option[Span] = Option(tracer.activeSpan())

    override def close(status: Status, trailers: Metadata): Unit = {
      if (!status.isOk()) {
        OpentracingHelpers.reportErrorToSpan(span, status.asException())
      }
      super.close(status, trailers)
    }
  }
}
