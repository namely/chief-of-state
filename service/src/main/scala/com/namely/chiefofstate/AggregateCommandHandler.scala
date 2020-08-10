package com.namely.chiefofstate

import akka.actor.ActorSystem
import akka.grpc.GrpcServiceException
import akka.grpc.scaladsl.SingleResponseRequestBuilder
import com.google.protobuf.any.Any
import com.namely.protobuf.chief_of_state.common.{MetaData => _}
import com.namely.protobuf.chief_of_state.common
import com.namely.protobuf.chief_of_state.persistence.{Event, State}
import com.namely.protobuf.chief_of_state.service.GetStateRequest
import com.namely.protobuf.chief_of_state.writeside.{
  HandleCommandRequest,
  HandleCommandResponse,
  WriteSideHandlerServiceClient
}
import com.namely.protobuf.chief_of_state.writeside.HandleCommandResponse.ResponseType.{Empty, PersistAndReply, Reply}
import com.namely.protobuf.chief_of_state.internal.RemoteCommand
import io.grpc.{Status, StatusRuntimeException}
import io.superflat.lagompb.{Command, CommandHandler}
import io.superflat.lagompb.protobuf.core._
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.concurrent.Future


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
      case remoteCommand: RemoteCommand => Try(handleRemoteCommand(remoteCommand, priorState, priorEventMeta))
      // otherwise throw
      case unhandled => Failure(new Exception(s"unhandled command type ${unhandled.companion.scalaDescriptor.fullName}"))
    }
  }

  /**
   * handler for GetStateRequest command
   *
   * @param command a getStateRequest
   * @param priorState the prior state for this entity
   * @param priorEventMeta the prior event meta
   * @return a command handler response indicating Reply or failure
   */
  def handleGetCommand(command: GetStateRequest,
                       priorState: State,
                       priorEventMeta: MetaData
  ): CommandHandlerResponse = {
    log.debug("[ChiefOfState] handling GetStateRequest")

    priorState.currentState
      .map(currentState => {
        log.debug(s"[ChiefOfState] found state for entity ${command.entityId}")
        CommandHandlerResponse()
          .withSuccessResponse(
            SuccessCommandHandlerResponse()
              .withNoEvent(com.google.protobuf.empty.Empty.defaultInstance)
          )
      })
      .getOrElse {
        log.error(s"[ChiefOfState] could not find state for entity ${command.entityId}")
        CommandHandlerResponse()
          .withFailedResponse(
            FailedCommandHandlerResponse()
              .withReason("entity not found")
              .withCause(FailureCause.InternalError)
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
  def handleRemoteCommand(remoteCommand: RemoteCommand, priorState: State, priorEventMeta: MetaData): CommandHandlerResponse = {
    log.debug("[ChiefOfState] handling gRPC command")

    // make blocking gRPC call to handler service
    val responseAttempt: Try[HandleCommandResponse] = Try {
      // construct the request message
      val handleCommandRequest = HandleCommandRequest()
        .withCommand(remoteCommand.getCommand)
        .withCurrentState(priorState.getCurrentState)
        .withMeta(Util.toCosMetaData(priorEventMeta))

      // create an akka gRPC request builder
      val futureResponse: Future[HandleCommandResponse] =
        remoteCommand.headers
        // initiate foldLeft with empty handleCommand request builder
        .foldLeft(writeSideHandlerServiceClient.handleCommand())(
          // for each header, add the appropriate string/bytes header
          (request, header) => {
            header.value match {
              case RemoteCommand.Header.Value.StringValue(value) => request.addHeader(header.key, value)
              case RemoteCommand.Header.Value.BytesValue(value) => request.addHeader(header.key, akka.util.ByteString(value.toByteArray))
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
      case Failure(exception) => handleRemoteResponseFailure(exception)
    }
  }

  /** Helper method to transform successful HandleCommandResponse into
    * a lagom-pb CommandHandlerResponse message
    *
    * @param response a HandleCommandResponse from write-side handler
    * @return an instance of CommandHandlerResponse
    */
  def handleRemoteResponseSuccess(response: HandleCommandResponse): CommandHandlerResponse = {
    response.responseType match {
      case PersistAndReply(persistAndReply) =>
        log.debug("[ChiefOfState] command handler return successfully. An event will be persisted...")
        val eventFQN: String = Util.getProtoFullyQualifiedName(persistAndReply.getEvent)

        log.debug(s"[ChiefOfState] command handler event to persist $eventFQN")

        if (handlerSetting.eventFQNs.contains(eventFQN)) {
          log.debug(s"[ChiefOfState] command handler event to persist $eventFQN is valid.")
          CommandHandlerResponse()
            .withSuccessResponse(
              SuccessCommandHandlerResponse()
                .withEvent(Any.pack(Event().withEvent(persistAndReply.getEvent)))
            )
        } else {
          log.error(
            s"[ChiefOfState] command handler event to persist $eventFQN is not configured. Failing request"
          )
          CommandHandlerResponse()
            .withFailedResponse(
              FailedCommandHandlerResponse()
                .withReason(s"received unknown event type $eventFQN")
                .withCause(FailureCause.ValidationError)
            )
        }

      case Reply(_) =>
        log.debug("[ChiefOfState] command handler return successfully. No event will be persisted...")
        CommandHandlerResponse()
          .withSuccessResponse(
            SuccessCommandHandlerResponse()
              .withNoEvent(com.google.protobuf.empty.Empty.defaultInstance)
          )

      case unhandled =>
        CommandHandlerResponse()
          .withFailedResponse(
            FailedCommandHandlerResponse()
              .withReason(s"command handler returned malformed event, ${unhandled.getClass.getName}")
              .withCause(FailureCause.InternalError)
          )
    }
  }

  /** helper method to transform errors from remote command handler into
    * appropriate CommandHandlerResponse
    *
    * @param throwable an exception from the handler gRPC call
    * @return a CommandHandlerResponse
    */
  def handleRemoteResponseFailure(throwable: Throwable): CommandHandlerResponse = {
    throwable match {
      case e: StatusRuntimeException =>
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

      case e: GrpcServiceException =>
        log.error(s"[ChiefOfState] handler gRPC failed with ${e.status.toString()}", e)
        CommandHandlerResponse()
          .withFailedResponse(
            FailedCommandHandlerResponse()
              .withReason(e.getStatus.toString)
              .withCause(FailureCause.InternalError)
          )

      case e: Throwable =>
        log.error(s"[ChiefOfState] gRPC handler critical failure", e)
        CommandHandlerResponse()
          .withFailedResponse(
            FailedCommandHandlerResponse()
              .withReason(
                s"Critical error occurred handling command, ${e.getMessage()}"
              )
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
