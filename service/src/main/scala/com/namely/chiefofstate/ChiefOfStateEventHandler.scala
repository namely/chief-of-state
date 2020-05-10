package com.namely.chiefofstate

import akka.actor.ActorSystem
import com.google.protobuf.any.Any
import com.namely.lagom.NamelyEventHandler
import com.namely.lagom.NamelyException
import com.namely.protobuf.chief_of_state.handler.HandleEventRequest
import com.namely.protobuf.chief_of_state.handler.HandleEventResponse
import com.namely.protobuf.chief_of_state.handler.HandlerServiceClient
import com.namely.protobuf.lagom.common.EventMeta
import scalapb.GeneratedMessage

import scala.concurrent.Await
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.concurrent.duration._

class ChiefOfStateEventHandler(actorSystem: ActorSystem, gRpcClient: HandlerServiceClient)
    extends NamelyEventHandler[Any](actorSystem) {

  override def handle(event: GeneratedMessage, priorState: Any, eventMeta: EventMeta): Any = {
    Try(
      gRpcClient.handleEvent(
        HandleEventRequest()
          .withEvent(Any.pack(event))
          .withCurrentState(priorState)
          .withMeta(Any.pack(eventMeta))
      )
    ) match {

      case Failure(e) =>
        throw new NamelyException(e.getMessage)

      case Success(eventualEventResponse: Future[HandleEventResponse]) =>
        Try {
          // this is a hack for the meantime
          //FIXME this is very bad
          Await.result(eventualEventResponse, 5.seconds)
        } match {
          case Failure(exception) => throw new NamelyException(exception.getMessage)
          case Success(handleEventResponse: HandleEventResponse) => handleEventResponse.getResultingState
        }
    }
  }
}
