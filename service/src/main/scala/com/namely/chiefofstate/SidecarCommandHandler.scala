package com.namely.chiefofstate

import akka.grpc.GrpcServiceException
import com.google.protobuf.any.Any
import com.namely.lagom._
import com.namely.protobuf.chief_of_state.handler.HandleCommandResponse.ResponseType
import com.namely.protobuf.chief_of_state.handler.{HandleCommandRequest, HandleCommandResponse}
import com.namely.protobuf.lagom.common.{NamelyRejectionCause, StateMeta}
import io.grpc.Status

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class SidecarCommandHandler extends NamelyCommandHandler[Any]{

  override def handle(command: NamelyCommand, state: Any, stateMeta: StateMeta): Try[NamelyHandlerResponse] = {

    val handleRequest = HandleCommandRequest()
        .withCommand(Any.pack(command.command))
        .withCurrentState(state)
        .withMeta(Any.pack(stateMeta))

    Try(
      HandlerClient.client.handleCommand(handleRequest)
    ) match {
      case Failure(exception: Throwable) =>
        exception match {
          case e: GrpcServiceException => Try(FailedResponse(e.status.getDescription, NamelyRejectionCause.InternalError))
          case _ => Try(FailedResponse(Status.INTERNAL.withDescription("").toString, NamelyRejectionCause.InternalError))
        }

      case Success(value: Future[HandleCommandResponse]) =>
        value.value match {
          case Some(value) =>
            value.get.responseType match {
              case ResponseType.Empty => Try(ReplyResponse)
              case ResponseType.PersistAndReply(value) => Try(PersistAndReplyResponse(value))
              case ResponseType.Reply(_) => Try(ReplyResponse)
            }
          case None => ???
      }
    }
  }
}
