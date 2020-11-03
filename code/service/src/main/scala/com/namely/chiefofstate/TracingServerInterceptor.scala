package com.namely.chiefofstate

import io.grpc._
import java.{util => ju}
import org.slf4j.{Logger, LoggerFactory}
import com.fasterxml.jackson.module.scala.deser.overrides
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall
import kamon.Kamon

/**
 * Intercepts gRPC headers and propagate them downstream via the gRPC context
 */
object TracingServerInterceptor extends ServerInterceptor {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  val CONTEXT_KEY: Context.Key[String] = Context.key[String]("some_uuid")
  val METADATA_KEY: Metadata.Key[String] = Metadata.Key.of("some_uuid", Metadata.ASCII_STRING_MARSHALLER)

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
    val span = Kamon.serverSpanBuilder(methodName, componentName).start()

    val someUuid: String = ju.UUID.randomUUID().toString()
    logger.warn(s"BEGIN uuid=$someUuid")

    val newCall = new SimpleForwardingServerCall[ReqT, RespT](call) {
      override def sendHeaders(responseHeaders: Metadata): Unit = {
        responseHeaders.put(METADATA_KEY, someUuid)
        super.sendHeaders(responseHeaders)
      }
    }

    val context: Context = Context.current().withValue(CONTEXT_KEY, someUuid)
    val listener = Contexts.interceptCall(context, newCall, headers, next)

    new SimpleForwardingServerCallListener[ReqT](listener) {
      override def onCancel(): Unit = {
        try {
          logger.warn(s"CANCEL uuid=$someUuid")
          super.onCancel()
        }
        finally {
          span.finish()
        }
      }

      override def onComplete(): Unit = {
        try {
          logger.warn(s"CANCEL uuid=$someUuid")
          super.onCancel()
        }
        finally {
          span.finish()
        }
      }
    }
  }

}
