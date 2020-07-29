package com.namely.chiefofstate

import akka.actor.ActorSystem
import akka.grpc.GrpcServiceException
import com.google.protobuf.any.Any
import com.namely.protobuf.chief_of_state.common.{MetaData => _}
import com.namely.protobuf.chief_of_state.common
import com.namely.protobuf.chief_of_state.persistence.{Event, State}
import com.namely.protobuf.chief_of_state.writeside.{
  HandleCommandRequest,
  HandleCommandResponse,
  WriteSideHandlerServiceClient
}
import com.namely.protobuf.chief_of_state.writeside.HandleCommandResponse.ResponseType.{Empty, PersistAndReply, Reply}
import io.grpc.{Status, StatusRuntimeException}
import io.superflat.lagompb.{Command, CommandHandler}
import io.superflat.lagompb.protobuf.core._
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import com.namely.protobuf.chief_of_state.service.GetStateRequest

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
) extends CommandHandler[State](actorSystem) {

  import AggregateCommandHandler.GRPC_FAILED_VALIDATION_STATUSES

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * general handle command implementation
   *
   * @param command the actual command to handle
   * @param priorState the priorState
   * @param priorEventMeta the priorEventMeta
   * @return
   */
  override def handle(command: Command, priorState: State, priorEventMeta: MetaData): Try[CommandHandlerResponse] = {
    command.command match {
      // handle get requests locally
      case getStateRequest: GetStateRequest => Try(handleGetCommand(getStateRequest, priorState, priorEventMeta))
      // handle all other requests in the gRPC handler
      case _ => Try(handleRemoteCommand(command, priorState, priorEventMeta))
    }
  }

  /** handler for GetStateRequest command
    *
    * @param command a getStateRequest
    * @param priorState the prior state for this entity
    * @param priorEventMeta the prior event meta
    * @return a command handler response indicating Reply or failure
    */
  def handleGetCommand(command: GetStateRequest, priorState: State, priorEventMeta: MetaData): CommandHandlerResponse = {
    log.debug("[ChiefOfState] handling GetStateRequest")

    priorState
      .currentState
      .map(currentState => {
        log.debug(s"[ChiefOfState] found state for entity ${command.entityId}")
        CommandHandlerResponse()
          .withSuccessResponse(
            SuccessCommandHandlerResponse()
              .withNoEvent(com.google.protobuf.empty.Empty.defaultInstance)
          )
      })
      .getOrElse({
        log.error(s"[ChiefOfState] could not find state for entity ${command.entityId}")
        CommandHandlerResponse()
          .withFailedResponse(
            FailedCommandHandlerResponse()
              .withReason("entity not found")
              .withCause(FailureCause.InternalError)
          )
      })
  }

  /** handler for commands that should be forwarded by gRPC handler service
    *
    * @param command a command to forward
    * @param priorState the prior state of the entity
    * @param priorEventMeta the prior event meta data
    * @return a CommandHandlerResponse
    */
  def handleRemoteCommand(command: Command, priorState: State, priorEventMeta: MetaData): CommandHandlerResponse = {
    log.debug("[ChiefOfState] handling gRPC command")

    val request = HandleCommandRequest()
      .withCommand(command.command.asInstanceOf[Any])
      .withCurrentState(priorState.getCurrentState)
      .withMeta(
        common
          .MetaData()
          .withData(priorEventMeta.data)
          .withRevisionDate(priorEventMeta.getRevisionDate)
          .withRevisionNumber(priorEventMeta.revisionNumber)
      )

    // await response from gRPC handler service
    val responseAttempt: Try[HandleCommandResponse] = Try({
      val futureResponse = writeSideHandlerServiceClient.handleCommand(request)
      Await.result(futureResponse, Duration.Inf)
    })

    responseAttempt match {
      case Success(response: HandleCommandResponse) =>
        response.responseType match {
          case PersistAndReply(persistAndReply) =>
            log.debug("[ChiefOfState]: command handler return successfully. An event will be persisted...")
            val eventFQN: String = Util.getProtoFullyQualifiedName(persistAndReply.getEvent)

            log.debug(s"[ChiefOfState]: command handler event to persist $eventFQN")

            if (handlerSetting.eventFQNs.contains(eventFQN)) {
              log.debug(s"[ChiefOfState]: command handler event to persist $eventFQN is valid.")
              CommandHandlerResponse()
                .withSuccessResponse(
                  SuccessCommandHandlerResponse()
                    .withEvent(Any.pack(Event().withEvent(persistAndReply.getEvent)))
                )
            } else {
              log.error(s"[ChiefOfState]: command handler event to persist $eventFQN is not configured. Failing request")
              CommandHandlerResponse()
                .withFailedResponse(
                  FailedCommandHandlerResponse()
                    .withReason(s"received unknown event type $eventFQN")
                    .withCause(FailureCause.ValidationError)
                )
            }

          case Reply(_) =>
            log.debug("[ChiefOfState]: command handler return successfully. No event will be persisted...")
            CommandHandlerResponse()
              .withSuccessResponse(
                SuccessCommandHandlerResponse()
                  .withNoEvent(com.google.protobuf.empty.Empty.defaultInstance)
              )

          case Empty =>
            CommandHandlerResponse()
              .withFailedResponse(
                FailedCommandHandlerResponse()
                  .withReason("command handler returned malformed event")
                  .withCause(FailureCause.InternalError)
              )
        }

      case Failure(e: StatusRuntimeException) =>
        val status: Status = e.getStatus()
        val reason: String = s"command failed (${status.getCode.name}) ${status.getDescription()}"
        log.error(s"[ChiefOfState] $reason")

        // handle specific gRPC error statuses
        val cause =
          if (GRPC_FAILED_VALIDATION_STATUSES.contains(status.getCode)) {
            FailureCause.ValidationError
          } else {
            FailureCause.InternalError
          }

        CommandHandlerResponse()
          .withFailedResponse(
            FailedCommandHandlerResponse()
              .withReason(reason)
              .withCause(cause)
          )

      case Failure(e: GrpcServiceException) =>
        log.error(s"[ChiefOfState] handler gRPC failed with ${e.status.toString()}", e)
        CommandHandlerResponse()
          .withFailedResponse(
            FailedCommandHandlerResponse()
              .withReason(e.getStatus.toString)
              .withCause(FailureCause.InternalError)
          )


      case Failure(e: Throwable) =>
        log.error(s"[ChiefOfState] gRPC handler critical failure", e)
        CommandHandlerResponse()
          .withFailedResponse(
            FailedCommandHandlerResponse()
              .withReason(s"Critical error occurred handling command ${command.command.getClass.getCanonicalName}, ${e.getMessage()}")
              .withCause(FailureCause.InternalError)
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
}
