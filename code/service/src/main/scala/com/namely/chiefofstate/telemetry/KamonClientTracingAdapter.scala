package com.namely.chiefofstate.common.telemetry

import io.grpc.ClientInterceptor
import io.grpc.{CallOptions, Channel, ClientCall, MethodDescriptor}
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall
import io.grpc.ClientCall.Listener
import io.grpc.Status
import io.grpc.Metadata
import org.slf4j.{Logger, LoggerFactory}
import kamon.Kamon
import kamon.trace.Span
import kamon.trace.Identifier
import kamon.context.HttpPropagation
import kamon.trace.Trace.SamplingDecision

/**
 * gRPC client intercpetor for propagating the current kamon context
 */
object KamonClientTracingAdapter extends ClientInterceptor {

  // $COVERAGE-OFF$

  val TraceIdentifier = Metadata.Key.of("X-B3-TraceId", Metadata.ASCII_STRING_MARSHALLER)
  val ParentSpanIdentifier = Metadata.Key.of("X-B3-ParentSpanId", Metadata.ASCII_STRING_MARSHALLER)
  val SpanIdentifier = Metadata.Key.of("X-B3-SpanId", Metadata.ASCII_STRING_MARSHALLER)
  val Sampled = Metadata.Key.of("X-B3-Sampled", Metadata.ASCII_STRING_MARSHALLER)
  val Flags = Metadata.Key.of("X-B3-Flags", Metadata.ASCII_STRING_MARSHALLER)

  final val logger: Logger = LoggerFactory.getLogger(getClass)

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
    import ErrorsClientInterceptor.logger

    new KamonClientTracingAdapter.CustomClientCall[T, U](next.newCall(method, callOptions), method)
  }

  /**
   * custom client call class for applying the custom listener
   *
   * @param call a call to intercept
   * @param tracer the tracer to use
   */
  class CustomClientCall[T, U](call: ClientCall[T, U], method: MethodDescriptor[T, U])
      extends SimpleForwardingClientCall[T, U](call) {

    var currentSpan: Option[Span] = None

    /**
     * on start of a call, start a child client span
     *
     * @param responseListener a custom response listener
     * @param headers the headers of the call
     */
    override def start(responseListener: Listener[U], headers: Metadata): Unit = {
      // start a new child span
      val span: Span = Kamon
        .clientSpanBuilder(method.getFullMethodName(), this.getClass.getName)
        .asChildOf(Kamon.currentSpan())
        .start()

      // set new span as the "current span"
      currentSpan = Some(span)

      // add tracing headers
      headers.put(TraceIdentifier, span.trace.id.string)
      headers.put(SpanIdentifier, span.id.string)

      if(span.parentId != null && span.parentId != Identifier.Empty) {
        headers.put(ParentSpanIdentifier, span.parentId.string)
      }

      span.trace.samplingDecision match {
        case SamplingDecision.Sample => headers.put(Sampled, "1")
        case SamplingDecision.DoNotSample => headers.put(Sampled, "0")
        case _ => {}
      }

      // continue starting the call
      super.start(new CustomListener[U](responseListener, span), headers)
    }

    /**
     * on cancel of call, close the span
     *
     * @param message
     * @param cause
     */
    override def cancel(message: String, cause: Throwable): Unit = {
      currentSpan.foreach(_.finish())
      super.cancel(message, cause)
    }
  }

  class CustomListener[U](listener: ClientCall.Listener[U], span: Span)
      extends SimpleForwardingClientCallListener[U](listener) {

    /**
     * on close of listener, close the span
     *
     * @param status the closing status
     * @param trailers trailing metadata
     */
    override def onClose(status: Status, trailers: Metadata): Unit = {
      // if not ok, report error
      if (!status.isOk()) {
        span.fail(status.toString())
      }

      span.finish()
      super.onClose(status, trailers)
    }
  }

  // $COVERAGE-ON$
}
