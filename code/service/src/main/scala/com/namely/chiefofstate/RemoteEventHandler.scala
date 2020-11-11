package com.namely.chiefofstate

import java.util.concurrent.TimeUnit

import com.namely.chiefofstate.config.GrpcConfig
import com.namely.protobuf.chiefofstate.v1.persistence.StateWrapper
import com.namely.protobuf.chiefofstate.v1.writeside.{HandleEventRequest, HandleEventResponse}
import com.namely.protobuf.chiefofstate.v1.writeside.WriteSideHandlerServiceGrpc.WriteSideHandlerServiceBlockingStub
import org.slf4j.{Logger, LoggerFactory}

import scala.util.Try

/**
 * handles a given event by making a rpc call
 *
 * @param grpcConfig the grpc config
 * @param writeHandlerServicetub the grpc client stub
 */
case class RemoteEventHandler(grpcConfig: GrpcConfig, writeHandlerServicetub: WriteSideHandlerServiceBlockingStub) {

  final val log: Logger = LoggerFactory.getLogger(getClass)

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
        s" sending request to the event handler to handle the given event ${event.typeUrl}"
      )
      writeHandlerServicetub
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
