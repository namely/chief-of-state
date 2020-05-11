package com.namely.chiefofstate

import akka.actor.ActorSystem
import com.google.protobuf.any.Any
import com.namely.lagom.NamelyEventHandler
import com.namely.lagom.NamelyException
import com.namely.protobuf.chief_of_state.handler.HandleEventRequest
import com.namely.protobuf.chief_of_state.handler.HandleEventResponse
import com.namely.protobuf.chief_of_state.handler.HandlerServiceClient
import com.namely.protobuf.chief_of_state.persistence.Event
import com.namely.protobuf.chief_of_state.persistence.State
import com.namely.protobuf.lagom.common.EventMeta
import scalapb.GeneratedMessage

import scala.concurrent.Await
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.concurrent.duration._

class ChiefOfStateEventHandler(actorSystem: ActorSystem, gRpcClient: HandlerServiceClient)
    extends NamelyEventHandler[State](actorSystem) {

  override def handle(event: GeneratedMessage, priorState: State, eventMeta: EventMeta): State = {
    Try(
      gRpcClient.handleEvent(
        HandleEventRequest()
          .withEvent(event.asInstanceOf[Event].getEvent)
          .withCurrentState(priorState.getCurrentState)
          .withMeta(Any.pack(eventMeta))
      )
    ) match {

      case Failure(e) =>
        throw new NamelyException(e.getMessage)

      case Success(eventualEventResponse: Future[HandleEventResponse]) =>
        Try {
          // this is a hack for the meantime
          //FIXME this is very bad
          Await.result(eventualEventResponse, Duration.Inf)
        } match {
          case Failure(exception) => throw new NamelyException(exception.getMessage)
          case Success(handleEventResponse: HandleEventResponse) =>
            priorState.update(
              _.currentState := handleEventResponse.getResultingState
            )
        }
    }
  }
}
