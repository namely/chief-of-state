package com.namely.chiefofstate.interceptors

import io.grpc.ClientInterceptor
import io.grpc.{CallOptions, Channel, ClientCall, MethodDescriptor}
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall
import io.grpc.Metadata
import io.grpc.ClientCall.Listener
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener
import io.grpc.Status
import org.slf4j.{Logger, LoggerFactory}

object TracingClientInterceptor extends ClientInterceptor {

  // private val logger: Logger = LoggerFactory.getLogger(getClass)

  override def interceptCall[ReqT, RespT](method: MethodDescriptor[ReqT, RespT],
                                          callOptions: CallOptions,
                                          next: Channel
  ): ClientCall[ReqT, RespT] = {

    val newCall = next.newCall(method, callOptions)

    new SimpleForwardingClientCall[ReqT, RespT](newCall) {
      // start a span
      val span = getSpanBuilder(method).start()

      // override the start method to close the span
      override def start(responseListener: Listener[RespT], headers: Metadata): Unit = {
        // special listener that always closes the span
        val tracingListener = new SimpleForwardingClientCallListener[RespT](responseListener) {
          override def onClose(status: Status, trailers: Metadata): Unit = {
            super.onClose(status, trailers)
            span.finish()
          }
        }
        // invoke parent .start with tracingListener
        super.start(tracingListener, headers)
      }
    }
  }

  /**
   * helper to yield a new span builder with current method details
   *
   * @param method a gRPC method descriptor
   * @return a kamon span builder
   */
  def getSpanBuilder(method: MethodDescriptor[_, _]): kamon.trace.SpanBuilder = {
    kamon.Kamon.clientSpanBuilder(
      method.getFullMethodName(),
      this.getClass.getName
    )
  }
}
