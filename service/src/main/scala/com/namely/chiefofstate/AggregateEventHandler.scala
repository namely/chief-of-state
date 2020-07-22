package com.namely.chiefofstate

import akka.actor.ActorSystem
import com.namely.protobuf.chief_of_state.common
import com.namely.protobuf.chief_of_state.persistence.{Event, State}
import com.namely.protobuf.chief_of_state.writeside.{
  HandleEventRequest,
  HandleEventResponse,
  WriteSideHandlerServiceClient
}
import io.superflat.lagompb.{EventHandler, GlobalException}
import io.superflat.lagompb.protobuf.core.MetaData
import org.slf4j.{Logger, LoggerFactory}
import scalapb.GeneratedMessage

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
 * ChiefOfStateEventHandler
 *
 * @param actorSystem                   the actor system
 * @param writeSideHandlerServiceClient the gRpcClient used to connect to the actual event handler
 * @param handlerSetting                the event handler setting
 */
class AggregateEventHandler(
    actorSystem: ActorSystem,
    writeSideHandlerServiceClient: WriteSideHandlerServiceClient,
    handlerSetting: HandlerSetting
) extends EventHandler[State](actorSystem) {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  override def handle(event: GeneratedMessage, priorState: State, eventMeta: MetaData): State = {
    Try(
      writeSideHandlerServiceClient.handleEvent(
        HandleEventRequest()
          .withEvent(event.asInstanceOf[Event].getEvent)
          .withCurrentState(priorState.getCurrentState)
          .withMeta(
            common
              .MetaData()
              .withData(eventMeta.data)
              .withRevisionDate(eventMeta.getRevisionDate)
              .withRevisionNumber(eventMeta.revisionNumber)
          )
      )
    ) match {

      case Failure(e) =>
        throw new GlobalException(e.getMessage)

      case Success(eventualEventResponse: Future[HandleEventResponse]) =>
        Try {
          Await.result(eventualEventResponse, Duration.Inf)
        } match {
          case Failure(exception) => throw new GlobalException(exception.getMessage)
          case Success(handleEventResponse: HandleEventResponse) =>
            val stateFQN: String = Util.getProtoFullyQualifiedName(handleEventResponse.getResultingState)

            log.debug(s"[ChiefOfState]: event handler state $stateFQN")

            if (handlerSetting.stateFQNs.contains(stateFQN)) {

              log.debug(s"[ChiefOfState]: event handler state $stateFQN is valid.")

              priorState.update(_.currentState := handleEventResponse.getResultingState)
            } else {
              throw new GlobalException(
                s"[ChiefOfState]: command handler state to persist $stateFQN is not configured. Failing request"
              )
            }
        }
    }
  }
}
