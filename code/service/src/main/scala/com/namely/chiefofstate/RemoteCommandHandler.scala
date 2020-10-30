package com.namely.chiefofstate

import java.util.concurrent.TimeUnit

import com.namely.chiefofstate.config.GrpcConfig
import com.namely.protobuf.chiefofstate.v1.persistence.StateWrapper
import com.namely.protobuf.chiefofstate.v1.writeside.{HandleCommandRequest, HandleCommandResponse}
import com.namely.protobuf.chiefofstate.v1.writeside.WriteSideHandlerServiceGrpc.WriteSideHandlerServiceBlockingStub
import org.slf4j.{Logger, LoggerFactory}

import scala.util.Try

/**
 * handles command via a gRPC call
 *
 * @param grpcConfig the grpc config
 * @param writeHandlerServicetub the grpc client stub
 */
case class RemoteCommandHandler(grpcConfig: GrpcConfig, writeHandlerServicetub: WriteSideHandlerServiceBlockingStub) {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * handles the given command and return an eventual response
   *
   * @param command the command to handle
   * @param priorState the aggregate state before the command to handle
   * @return an eventual HandleCommandResponse
   */
  def handleCommand(command: com.google.protobuf.any.Any, priorState: StateWrapper): Try[HandleCommandResponse] = {
    Try {
      log.debug(
        s"[ChiefOfState] sending request to the command handler to handle the given command ${command.typeUrl}"
      )
      writeHandlerServicetub
        .withDeadlineAfter(grpcConfig.client.timeout, TimeUnit.MILLISECONDS)
        .handleCommand(
          HandleCommandRequest()
            .withPriorState(priorState.getState)
            .withCommand(command)
            .withPriorEventMeta(priorState.getMeta)
        )
    }
  }
}
