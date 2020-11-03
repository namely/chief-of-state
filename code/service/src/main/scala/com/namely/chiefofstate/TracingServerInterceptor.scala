package com.namely.chiefofstate

import io.grpc._
import java.{util => ju}
import org.slf4j.{Logger, LoggerFactory}
import com.fasterxml.jackson.module.scala.deser.overrides
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall
import kamon.Kamon
import kamon.trace.Span
import kamon.trace.SpanBuilder
import kamon.instrumentation.futures.scala.ScalaFutureInstrumentation.trace
import scala.concurrent.Future
import scala.util.Try

/**
 * Intercepts gRPC headers and propagate them downstream via the gRPC context
 */
object TracingServerInterceptor extends ServerInterceptor {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  val SPAN_KEY: Context.Key[Span] = Context.key[Span]("kamon_span")

  /**
   * helper that yields a child span to the active span on this context
   *
   * @param processName some process name
   * @return a span builder
   */
  def getChildSpanBuilder(processName: String): SpanBuilder = {
    kamon.Kamon
      .spanBuilder(processName)
      .asChildOf(SPAN_KEY.get())
  }

  /**
   * helper to create a span for the duration of some future
   * as a child of the RPC parent span
   *
   * @param processName some name for the process
   * @param future a future to execute
   * @return the completed future with the span completed
   */
  def traceFuture[T](processName: String)(future: => Future[T]): Future[T] = {
    trace(getChildSpanBuilder(processName))(future)
  }

  /**
   * helper to create a span for the duration of some future
   * as a child of the RPC parent span
   *
   * @param future a future to execute
   */
  def traceFuture[T](future: => Future[T]): Future[T] = {
    traceFuture("runFuture")(future)
  }

  /**
   * intercepts the request headers
   *
   * @param call the gRPC call
   * @param headers the request headers
   * @param next the next request call
   * @tparam ReqT the type of the request
   * @tparam RespT the type of the response
   * @return
   */
  override def interceptCall[ReqT, RespT](
    call: ServerCall[ReqT, RespT],
    headers: Metadata,
    next: ServerCallHandler[ReqT, RespT]
  ): ServerCall.Listener[ReqT] = {

    val methodName: String = call.getMethodDescriptor().getFullMethodName()
    val componentName: String = this.getClass().getName()

    val span: Span = Kamon.serverSpanBuilder(methodName, componentName).start()

    logger.debug(s"method=${methodName}, span.id=${span.id}")

    // create a context with the kamon context & span injected
    val newContext: Context = Context
      .current()
      .withValue(CONTEXT_KEY, Kamon.currentContext())
      .withValue(SPAN_KEY, span)

    // create the listener with this context
    val listener = Contexts.interceptCall(newContext, call, headers, next)

    // create the forwarding listener and override the completion methods
    // to close the span
    new SimpleForwardingServerCallListener[ReqT](listener) {
      override def onCancel(): Unit = {
        try {
          super.onCancel()
        } finally {
          span.finish()
        }
      }

      override def onComplete(): Unit = {
        try {
          super.onCancel()
        } finally {
          span.finish()
        }
      }
    }
  }

}
