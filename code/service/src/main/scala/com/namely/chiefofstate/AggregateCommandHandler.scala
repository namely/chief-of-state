package com.namely.chiefofstate

import akka.actor.ActorSystem
import akka.grpc.GrpcServiceException
import com.google.protobuf.any.Any
import com.namely.protobuf.chiefofstate.v1.common.{MetaData => _}
import com.namely.protobuf.chiefofstate.v1.internal.RemoteCommand
import com.namely.protobuf.chiefofstate.v1.service.GetStateRequest
import com.namely.protobuf.chiefofstate.v1.writeside.{
  HandleCommandRequest,
  HandleCommandResponse,
  WriteSideHandlerServiceClient
}
import com.namely.chiefofstate.config.HandlerSetting
import io.grpc.{Status, StatusRuntimeException}
import io.superflat.lagompb.{CommandHandler, ProtosRegistry}
import io.superflat.lagompb.protobuf.v1.core._
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

/**
 * ChiefOfStateCommandHandler
 *
 * @param actorSystem                   the actor system
 * @param writeSideHandlerServiceClient the gRpcClient used to connect to the actual command handler
 * @param handlerSetting                the command handler setting
 */
class AggregateCommandHandler(
  actorSystem: ActorSystem,
  writeSideHandlerServiceClient: WriteSideHandlerServiceClient,
  handlerSetting: HandlerSetting
) extends CommandHandler {

  import AggregateCommandHandler.GRPC_FAILED_VALIDATION_STATUSES

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
    * entrypoint command handler that unpacks the command proto and calls
    * the typed parameter
    *
    * @param command
    * @param priorState
    * @param priorEventMeta
    * @return
    */
  final def handle(command: Any, priorState: Any, priorEventMeta: MetaData): Try[CommandHandlerResponse] = {
    ProtosRegistry.unpackAny(command) match {
      case Failure(exception) =>
        Failure(exception)

      case Success(innerCommand) =>
        handleTyped(
          command = innerCommand,
          priorState = priorState,
          priorEventMeta = priorEventMeta
        )
    }
  }

  /**
   * general handle command implementation
   *
   * @param command the actual command to handle
   * @param priorState the priorState
   * @param priorEventMeta the priorEventMeta
   * @return
   */
  def handleTyped(command: scalapb.GeneratedMessage, priorState: Any, priorEventMeta: MetaData): Try[CommandHandlerResponse] = {
    command match {
      // handle get requests locally
      case getStateRequest: GetStateRequest => Try(handleGetCommand(getStateRequest, priorEventMeta))
      // handle all other requests in the gRPC handler
      case remoteCommand: RemoteCommand => Try(handleRemoteCommand(remoteCommand, priorState, priorEventMeta))
      // otherwise throw
      case unhandled =>
        Failure(new Exception(s"unhandled command type ${unhandled.companion.scalaDescriptor.fullName}"))
    }
  }

  /**
   * handler for GetStateRequest command
   *
   * @param command a getStateRequest
   * @param priorEventMeta the prior event meta
   * @return a command handler response indicating Reply or failure
   */
  def handleGetCommand(command: GetStateRequest,
                       priorEventMeta: MetaData
  ): CommandHandlerResponse = {
    log.debug("[ChiefOfState] handling GetStateRequest")

    // use revision number to determine if there was a prior state
    if (priorEventMeta.revisionNumber > 0) {
      log.debug(s"[ChiefOfState] found state for entity ${command.entityId}")
      CommandHandlerResponse()
        .withSuccessResponse(
          SuccessCommandHandlerResponse()
            .withNoEvent(com.google.protobuf.empty.Empty.defaultInstance)
        )
    } else {
      log.error(s"[ChiefOfState] could not find state for entity ${command.entityId}")
      CommandHandlerResponse()
        .withFailedResponse(
          AggregateCommandHandler.GET_STATE_NOT_FOUND_FAILURE
        )
    }
  }

  /**
   * handler for commands that should be forwarded by gRPC handler service
   *
   * @param remoteCommand a Remote Command to forward to write handler
   * @param priorState the prior state of the entity
   * @param priorEventMeta the prior event meta data
   * @return a CommandHandlerResponse
   */
  def handleRemoteCommand(remoteCommand: RemoteCommand,
                          priorState: Any,
                          priorEventMeta: MetaData
  ): CommandHandlerResponse = {
    log.debug("[ChiefOfState] handling gRPC command")

    // make blocking gRPC call to handler service
    val responseAttempt: Try[HandleCommandResponse] = Try {
      // construct the request message
      val handleCommandRequest = HandleCommandRequest(command=remoteCommand.command)
        .withCurrentState(priorState)
        .withMeta(Util.toCosMetaData(priorEventMeta))

      // create an akka gRPC request builder
      val futureResponse: Future[HandleCommandResponse] =
        remoteCommand.headers
          // initiate foldLeft with empty handleCommand request builder
          .foldLeft(writeSideHandlerServiceClient.handleCommand())(
            // for each header, add the appropriate string/bytes header
            (request, header) => {
              header.value match {
                case RemoteCommand.Header.Value.StringValue(value) =>
                  request.addHeader(header.key, value)
                case RemoteCommand.Header.Value.BytesValue(value) =>
                  request.addHeader(header.key, akka.util.ByteString(value.toByteArray))
                case unhandled => throw new Exception(s"unhandled gRPC header type, ${unhandled.getClass.getName}")
              }
            }
          )
          .invoke(handleCommandRequest)

      // await response and return
      Await.result(futureResponse, Duration.Inf)
    }

    responseAttempt match {
      case Success(response: HandleCommandResponse) => handleRemoteResponseSuccess(response)
      case Failure(exception)                       => handleRemoteResponseFailure(exception)
    }
  }

  /**
   * Helper method to transform successful HandleCommandResponse into
   * a lagom-pb CommandHandlerResponse message
   *
   * @param response a HandleCommandResponse from write-side handler
   * @return an instance of CommandHandlerResponse
   */
  def handleRemoteResponseSuccess(response: HandleCommandResponse): CommandHandlerResponse = {
    response.event match {
      case Some(event) =>

        log.debug("[ChiefOfState] command handler return successfully. An event will be persisted...")

        val eventFQN: String = Util.getProtoFullyQualifiedName(event)

        if (handlerSetting.enableProtoValidations && !handlerSetting.eventFQNs.contains(eventFQN)) {
          log.error(s"[ChiefOfState] command handler returned unknown event type, $eventFQN")
          CommandHandlerResponse()
            .withFailedResponse(
              FailedCommandHandlerResponse()
                .withReason(s"received unknown event type $eventFQN")
                .withCause(FailureCause.VALIDATION_ERROR)
            )
        } else {
          log.debug(s"[ChiefOfState] command handler event to persist $eventFQN is valid.")
            CommandHandlerResponse()
              .withSuccessResponse(
                SuccessCommandHandlerResponse()
                  .withEvent(event)
              )
        }

      case None =>
        log.debug("[ChiefOfState] command handler return successfully. No event will be persisted...")
        CommandHandlerResponse()
          .withSuccessResponse(
            SuccessCommandHandlerResponse()
              .withNoEvent(com.google.protobuf.empty.Empty.defaultInstance)
          )
    }
  }

  /**
   * helper method to transform errors from remote command handler into
   * appropriate CommandHandlerResponse
   *
   * @param throwable an exception from the handler gRPC call
   * @return a CommandHandlerResponse
   */
  def handleRemoteResponseFailure(throwable: Throwable): CommandHandlerResponse = {
    throwable match {
      case e: StatusRuntimeException =>
        val status: Status = e.getStatus
        val reason: String = s"command failed (${status.getCode.name}) ${status.getDescription}"
        log.error(s"[ChiefOfState] $reason")

        // handle specific gRPC error statuses
        val cause =
          if (GRPC_FAILED_VALIDATION_STATUSES.contains(status.getCode)) {
            FailureCause.VALIDATION_ERROR
          } else {
            FailureCause.INTERNAL_ERROR
          }

        CommandHandlerResponse()
          .withFailedResponse(
            FailedCommandHandlerResponse()
              .withReason(reason)
              .withCause(cause)
          )

      case e: GrpcServiceException =>
        log.error(s"[ChiefOfState] handler gRPC failed with ${e.status.toString}", e)
        CommandHandlerResponse()
          .withFailedResponse(
            FailedCommandHandlerResponse()
              .withReason(e.getStatus.toString)
              .withCause(FailureCause.INTERNAL_ERROR)
          )

      case e: Throwable =>
        log.error(s"[ChiefOfState] gRPC handler critical failure", e)
        CommandHandlerResponse()
          .withFailedResponse(
            FailedCommandHandlerResponse()
              .withReason(
                s"Critical error occurred handling command, ${e.getMessage}"
              )
              .withCause(FailureCause.INTERNAL_ERROR)
          )
    }
  }
}

/**
 * companion object
 */
object AggregateCommandHandler {

  // statuses that should be considered validation errors
  // in the command handler
  val GRPC_FAILED_VALIDATION_STATUSES: Set[Status.Code] = Set(
    Status.Code.INVALID_ARGUMENT,
    Status.Code.ALREADY_EXISTS,
    Status.Code.FAILED_PRECONDITION,
    Status.Code.OUT_OF_RANGE,
    Status.Code.PERMISSION_DENIED
  )

  // constant failure for entity not found
  val GET_STATE_NOT_FOUND_FAILURE: FailedCommandHandlerResponse =
    FailedCommandHandlerResponse()
      .withReason("entity not found")
      .withCause(FailureCause.INTERNAL_ERROR)
}
