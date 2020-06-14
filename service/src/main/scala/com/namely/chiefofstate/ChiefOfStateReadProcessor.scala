package com.namely.chiefofstate

import akka.Done
import akka.actor.ActorSystem
import com.google.protobuf.any.Any
import com.lightbend.lagom.scaladsl.persistence.slick.SlickReadSide
import com.namely.lagom.{NamelyException, NamelySlickReadSide, NamelyState}
import com.namely.protobuf.chief_of_state.handler.{HandleReadSideRequest, HandleReadSideResponse, HandlerServiceClient}
import com.namely.protobuf.chief_of_state.persistence.{Event, State}
import scalapb.{GeneratedMessage, GeneratedMessageCompanion}
import slick.dbio.{DBIO, DBIOAction}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

/**
 * ChiefOfStateReadProcessor
 *
 * @param actorSystem    the actor system
 * @param gRpcClient     the gRpcClient used to connect to the actual readSide handler
 * @param handlerSetting the readSide handler setting
 * @param readSide       the lagom readSide object that helps feed from events emitted in the journal
 */
class ChiefOfStateReadProcessor(
    actorSystem: ActorSystem,
    gRpcClient: HandlerServiceClient,
    handlerSetting: ChiefOfStateHandlerSetting,
    readSide: SlickReadSide
) extends NamelySlickReadSide[State](readSide) {
  // $COVERAGE-OFF$
  override def readSideId: String = "chiefOfState"

  override def aggregateStateCompanion: GeneratedMessageCompanion[State] = State

  // $COVERAGE-ON$

  override def handle(event: GeneratedMessage, namelyState: NamelyState[State]): DBIO[Done] = {
    event match {
      case e: Event =>
        Try(
          gRpcClient.handleReadSide(
            HandleReadSideRequest()
              .withEvent(e.getEvent)
              .withState(namelyState.state.getCurrentState)
              .withMeta(Any.pack(namelyState.eventMeta))
          )
        ) match {
          case Failure(exception) =>
            log.error(s"[ChiefOfState]: unable to retrieve command handler response due to ${exception.getMessage}")
            DBIOAction.failed(throw new NamelyException(exception.getMessage))
          case Success(eventualReadSideResponse: Future[HandleReadSideResponse]) =>
            Try {
              Await.result(eventualReadSideResponse, Duration.Inf)
            } match {
              case Failure(exception) =>
                DBIOAction.failed(throw new NamelyException(s"[ChiefOfState]: ${exception.getMessage}"))
              case Success(value) =>
                if (value.successful) DBIOAction.successful(Done)
                else DBIOAction.failed(throw new NamelyException("[ChiefOfState]: unable to handle readSide"))
            }
        }
      case _ =>
        DBIOAction.failed(
          throw new NamelyException(s"[ChiefOfState]: event ${event.companion.scalaDescriptor.fullName} not handled")
        )
    }
  }
}
