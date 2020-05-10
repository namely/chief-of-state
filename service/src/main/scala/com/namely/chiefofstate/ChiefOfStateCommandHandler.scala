package com.namely.chiefofstate

import akka.actor.ActorSystem
import akka.grpc.GrpcServiceException
import com.google.protobuf.any.Any
import com.namely.lagom._
import com.namely.protobuf.chief_of_state.handler.HandleCommandResponse.ResponseType._
import com.namely.protobuf.chief_of_state.handler.HandleCommandRequest
import com.namely.protobuf.chief_of_state.handler.HandleCommandResponse
import com.namely.protobuf.chief_of_state.handler.HandlerServiceClient
import com.namely.protobuf.chief_of_state.persistence.Event
import com.namely.protobuf.chief_of_state.persistence.State
import com.namely.protobuf.lagom.common._
import io.grpc.Status
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class ChiefOfStateCommandHandler(actorSystem: ActorSystem, gRpcClient: HandlerServiceClient)
    extends NamelyCommandHandler[State](actorSystem) {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Handle command
   *
   * @param command
   * @param priorState
   * @param priorEventMeta
   * @return
   */
  override def handle(
      command: NamelyCommand,
      priorState: State,
      priorEventMeta: EventMeta
  ): Try[CommandHandlerResult] = {
    Try(
      gRpcClient.handleCommand(
        HandleCommandRequest()
          .withCommand(command.command.asInstanceOf[Any])
          .withCurrentState(priorState.getCurrentState)
          .withMeta(Any.pack(priorEventMeta))
      )
    ) match {

      case Failure(exception: Throwable) =>
        exception match {
          case e: GrpcServiceException =>
            Try(
              CommandHandlerResult()
                .withFailedResult(
                  FailedResult()
                    .withReason(e.status.toString)
                    .withCause(FailureCause.InternalError)
                )
            )

          case _ =>
            Try(
              CommandHandlerResult()
                .withFailedResult(
                  FailedResult()
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
          // this is a hack for the meantime
          //FIXME this is very bad.
          Await.result(future, Duration.Inf)
        } match {
          case Failure(exception) =>
            // this situation will never occur but for the sake of syntax
            log.debug(
              s"[ChiefOfState]: unable to retrieve command handler response due to ${exception.getMessage}"
            )
            Try(
              CommandHandlerResult()
                .withFailedResult(
                  FailedResult()
                    .withReason(new GrpcServiceException(Status.UNAVAILABLE).toString)
                    .withCause(FailureCause.InternalError)
                )
            )
          case Success(handleCommandResponse: HandleCommandResponse) =>
            handleCommandResponse.responseType match {
              case PersistAndReply(persistAndReply) =>
                log.debug("[ChiefOfState]: command handler return successfully. An event will be persisted...")

                Try(
                  CommandHandlerResult()
                    .withSuccessResult(
                      SuccessResult()
                        .withEvent(Any.pack(Event().withEvent(persistAndReply.getEvent)))
                    )
                )
              case Reply(_) =>
                log.debug("[ChiefOfState]: command handler return successfully. No event will be persisted...")

                Try(
                  CommandHandlerResult()
                    .withSuccessResult(
                      SuccessResult()
                        .withNoEvent(com.google.protobuf.empty.Empty.defaultInstance)
                    )
                )
              case Empty =>
                // this situation will never occur but for the sake of syntax
                log.debug("[ChiefOfState]: command handler return weird successful response...")

                Try(
                  CommandHandlerResult()
                    .withFailedResult(
                      FailedResult()
                        .withReason(new GrpcServiceException(Status.INTERNAL).toString)
                        .withCause(FailureCause.InternalError)
                    )
                )
            }
        }
    }
  }
}
