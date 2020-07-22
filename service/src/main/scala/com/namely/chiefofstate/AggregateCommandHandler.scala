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
import io.grpc.Status
import io.superflat.lagompb.{Command, CommandHandler}
import io.superflat.lagompb.protobuf.core._
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
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
) extends CommandHandler[State](actorSystem) {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Handle command
   *
   * @param command the actual command to handle
   * @param priorState the priorState
   * @param priorEventMeta the priorEventMeta
   * @return
   */
  override def handle(command: Command, priorState: State, priorEventMeta: MetaData): Try[CommandHandlerResponse] = {
    Try(
      writeSideHandlerServiceClient.handleCommand(
        HandleCommandRequest()
          .withCommand(command.command.asInstanceOf[Any])
          .withCurrentState(priorState.getCurrentState)
          .withMeta(
            common
              .MetaData()
              .withData(priorEventMeta.data)
              .withRevisionDate(priorEventMeta.getRevisionDate)
              .withRevisionNumber(priorEventMeta.revisionNumber)
          )
      )
    ) match {

      case Failure(exception: Throwable) =>
        exception match {
          case e: GrpcServiceException =>
            Try(
              CommandHandlerResponse()
                .withFailedResponse(
                  FailedCommandHandlerResponse()
                    .withReason(e.status.toString)
                    .withCause(FailureCause.InternalError)
                )
            )

          case _ =>
            Try(
              CommandHandlerResponse()
                .withFailedResponse(
                  FailedCommandHandlerResponse()
                    .withReason(
                      new GrpcServiceException(
                        Status.INTERNAL.withDescription(
                          s"Error occurred. Unable to handle command ${command.command.getClass.getCanonicalName}"
                        )
                      ).toString
                    )
                    .withCause(FailureCause.InternalError)
                )
            )
        }

      case Success(future: Future[HandleCommandResponse]) =>
        Try {
          Await.result(future, Duration.Inf)
        } match {
          case Failure(exception) =>
            // this situation will never occur but for the sake of syntax
            log.error(s"[ChiefOfState]: unable to retrieve command handler response due to ${exception.getMessage}")
            Try(
              CommandHandlerResponse()
                .withFailedResponse(
                  FailedCommandHandlerResponse()
                    .withReason(new GrpcServiceException(Status.UNAVAILABLE).toString)
                    .withCause(FailureCause.InternalError)
                )
            )
          case Success(handleCommandResponse: HandleCommandResponse) =>
            handleCommandResponse.responseType match {
              case PersistAndReply(persistAndReply) =>
                log.debug("[ChiefOfState]: command handler return successfully. An event will be persisted...")

                val eventFQN: String = Util.getProtoFullyQualifiedName(persistAndReply.getEvent)

                log.debug(s"[ChiefOfState]: command handler event to persist $eventFQN")

                if (handlerSetting.eventFQNs.contains(eventFQN)) {
                  log.debug(s"[ChiefOfState]: command handler event to persist $eventFQN is valid.")

                  Try(
                    CommandHandlerResponse()
                      .withSuccessResponse(
                        SuccessCommandHandlerResponse()
                          .withEvent(Any.pack(Event().withEvent(persistAndReply.getEvent)))
                      )
                  )
                } else {
                  log.debug(
                    s"[ChiefOfState]: command handler event to persist $eventFQN is not configured. Failing request"
                  )

                  Try(
                    CommandHandlerResponse()
                      .withFailedResponse(
                        FailedCommandHandlerResponse()
                          .withReason(new GrpcServiceException(Status.INVALID_ARGUMENT).toString)
                          .withCause(FailureCause.ValidationError)
                      )
                  )
                }
              case Reply(_) =>
                log.debug("[ChiefOfState]: command handler return successfully. No event will be persisted...")

                Try(
                  CommandHandlerResponse()
                    .withSuccessResponse(
                      SuccessCommandHandlerResponse()
                        .withNoEvent(com.google.protobuf.empty.Empty.defaultInstance)
                    )
                )
              case Empty =>
                // this situation will never occur but for the sake of syntax
                log.debug("[ChiefOfState]: command handler return weird successful response...")

                Try(
                  CommandHandlerResponse()
                    .withFailedResponse(
                      FailedCommandHandlerResponse()
                        .withReason(new GrpcServiceException(Status.INTERNAL).toString)
                        .withCause(FailureCause.InternalError)
                    )
                )
            }
        }
    }
  }
}
