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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scalapb.GeneratedMessage

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class ChiefOfStateEventHandler(
    actorSystem: ActorSystem,
    gRpcClient: HandlerServiceClient,
    handlerSetting: ChiefOfStateHandlerSetting
) extends NamelyEventHandler[State](actorSystem) {

  final val log: Logger = LoggerFactory.getLogger(getClass)

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
          Await.result(eventualEventResponse, Duration.Inf)
        } match {
          case Failure(exception) => throw new NamelyException(exception.getMessage)
          case Success(handleEventResponse: HandleEventResponse) =>
            val stateFQN: String = ChiefOfStateHelper.getProtoFullyQualifiedName(handleEventResponse.getResultingState)

            log.debug(s"[ChiefOfState]: event handler state $stateFQN")

            if (handlerSetting.stateProtoFQN.equals(stateFQN)) {

              log.debug(s"[ChiefOfState]: event handler state $stateFQN is valid.")

              priorState.update(
                _.currentState := handleEventResponse.getResultingState
              )
            } else {
              throw new NamelyException(
                s"[ChiefOfState]: command handler state to perist $stateFQN is not configured. Failing request"
              )
            }
        }
    }
  }
}
