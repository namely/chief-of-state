package com.namely.chiefofstate

import java.util.concurrent.TimeUnit

import com.namely.chiefofstate.config.GrpcConfig
import com.namely.protobuf.chiefofstate.v1.persistence.StateWrapper
import com.namely.protobuf.chiefofstate.v1.writeside.{HandleEventRequest, HandleEventResponse}
import com.namely.protobuf.chiefofstate.v1.writeside.WriteSideHandlerServiceGrpc.WriteSideHandlerServiceBlockingStub
import org.slf4j.{Logger, LoggerFactory}

import scala.util.Try
import io.opentracing.contrib.grpc.TracingClientInterceptor
import io.opentracing.util.GlobalTracer

/**
 * handles a given event by making a rpc call
 *
 * @param grpcConfig the grpc config
 * @param writeHandlerServicetub the grpc client stub
 */
case class RemoteEventHandler(grpcConfig: GrpcConfig, writeHandlerServicetub: WriteSideHandlerServiceBlockingStub) {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  lazy val clientInterceptor = TracingClientInterceptor
    .newBuilder()
    .withTracer(GlobalTracer.get())
    .build()

  /**
   * handles the given event and return an eventual response
   *
   * @param event the event to handle
   * @param priorState the aggregate prior state
   * @return the eventual HandleEventResponse
   */
  def handleEvent(event: com.google.protobuf.any.Any, priorState: StateWrapper): Try[HandleEventResponse] = {
    Try {
      log.debug(
        s"sending request to the event handler to handle the given event ${event.typeUrl}"
      )

      writeHandlerServicetub
        .withInterceptors(clientInterceptor)
        .withDeadlineAfter(grpcConfig.client.timeout, TimeUnit.MILLISECONDS)
        .handleEvent(
          HandleEventRequest()
            .withEvent(event)
            .withPriorState(priorState.getState)
            .withEventMeta(priorState.getMeta)
        )
    }
  }
}
