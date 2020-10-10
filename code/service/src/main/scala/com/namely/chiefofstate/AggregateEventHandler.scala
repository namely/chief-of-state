package com.namely.chiefofstate

import akka.actor.ActorSystem
import com.google.protobuf.any.Any
import com.namely.chiefofstate.config.HandlerSetting
import com.namely.protobuf.chiefofstate.v1.writeside.{
  HandleEventRequest,
  HandleEventResponse,
  WriteSideHandlerServiceClient
}
import io.superflat.lagompb.EventHandler
import io.superflat.lagompb.protobuf.v1.core.MetaData
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
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
) extends EventHandler {

  /**
   * this will invoke the custom dispatcher to help send the request to
   * the event handler without disrupting the free-flow of the aggregate
   */
  implicit val executionContext: ExecutionContextExecutor =
    actorSystem.dispatchers.lookup(handlerSetting.eventHandlerDispatcher)

  final val log: Logger = LoggerFactory.getLogger(getClass)

  override def handle(event: Any, priorState: Any, eventMeta: MetaData): Any = {
    Try(
      writeSideHandlerServiceClient.handleEvent(
        HandleEventRequest()
          .withEvent(event)
          .withPriorState(priorState)
          .withEventMeta(Util.toCosMetaData(eventMeta))
      )
    ) match {

      case Failure(e) =>
        throw new Exception(e.getMessage)

      case Success(eventualEventResponse: Future[HandleEventResponse]) =>
        Try {
          Await.result(eventualEventResponse, Duration.Inf)
        } match {
          case Failure(exception) => throw new Exception(exception.getMessage)
          case Success(handleEventResponse: HandleEventResponse) =>
            val stateFQN: String = Util.getProtoFullyQualifiedName(handleEventResponse.getResultingState)
            log.debug(s"[ChiefOfState]: received event handler state $stateFQN")

            // if enabled, validate the state type url returned by event handler
            if (handlerSetting.enableProtoValidations && !handlerSetting.stateFQNs.contains(stateFQN)) {
              log.error(
                s"[ChiefOfState]: command handler state to persist $stateFQN is not configured. Failing request"
              )
              throw new Exception(s"received unknown state $stateFQN")
            }

            // pass through state returned by event handler
            handleEventResponse.getResultingState
        }
    }
  }
}
