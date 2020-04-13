package com.namely.chiefofstate

import akka.grpc.GrpcServiceException
import com.google.protobuf.any.Any
import com.namely.lagom._
import com.namely.protobuf.chief_of_state.handler.HandleCommandRequest
import com.namely.protobuf.chief_of_state.handler.HandleCommandResponse
import com.namely.protobuf.chief_of_state.handler.HandleCommandResponse.ResponseType
import com.namely.protobuf.lagom.common.NamelyRejectionCause
import com.namely.protobuf.lagom.common.StateMeta
import io.grpc.Status

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class SidecarCommandHandler extends NamelyCommandHandler[Any] {

  override def handle(command: NamelyCommand, state: Any, stateMeta: StateMeta): Try[NamelyHandlerResponse] = {

    val handleRequest = HandleCommandRequest()
      .withCommand(Any.pack(command.command))
      .withCurrentState(state)
      .withMeta(Any.pack(stateMeta))

    Try(
      HandlerClient.client.handleCommand(handleRequest)
      // NOTE: Do we need to close the client during failure to avoid leaking?
      // https://doc.akka.io/docs/akka-grpc/current/client/details.html#client-lifecycle
    ) match {

      case Failure(exception: Throwable) =>
        exception match {
          case e: GrpcServiceException =>
            Try(
              FailedResponse(
                e.status.toString,
                NamelyRejectionCause.InternalError
              )
            )

          case _ =>
            Try(
              FailedResponse(
                new GrpcServiceException(
                  Status.INTERNAL.withDescription(
                    s"Error occured.unable to handle command ${command.command.getClass.getCanonicalName}"
                  )
                ).toString,
                NamelyRejectionCause.InternalError
              )
            )
        }

      case Success(value: Future[HandleCommandResponse]) =>
        value.value match {

          case Some(value) =>
            value.get.responseType match {
              case ResponseType.Empty => Try(ReplyResponse)
              case ResponseType.PersistAndReply(value) => Try(PersistAndReplyResponse(value))
              case ResponseType.Reply(_) => Try(ReplyResponse)
            }

          case None =>
            Try(
              FailedResponse(
                new GrpcServiceException(
                  Status.INTERNAL.withDescription(
                    s"unable to handle command ${command.command.getClass.getCanonicalName}"
                  )
                ).toString,
                NamelyRejectionCause.InternalError
              )
            )
        }
    }
  }
}