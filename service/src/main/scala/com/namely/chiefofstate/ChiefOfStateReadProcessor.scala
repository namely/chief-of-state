package com.namely.chiefofstate

import akka.Done
import akka.actor.ActorSystem
import com.google.protobuf.any.Any
import com.namely.protobuf.chief_of_state.handler.{HandleReadSideRequest, HandleReadSideResponse, HandlerServiceClient}
import com.namely.protobuf.chief_of_state.persistence.{Event, State}
import lagompb.{LagompbConfig, LagompbException}
import lagompb.core.MetaData
import lagompb.readside.LagompbSlickProjection
import scalapb.{GeneratedMessage, GeneratedMessageCompanion}
import slick.dbio.{DBIO, DBIOAction}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

/**
 * ChiefOfStateReadProcessor
 *
 * @param actorSystem    the actor system
 * @param gRpcClient     the gRpcClient used to connect to the actual readSide handler
 * @param handlerSetting the readSide handler settingthe lagom readSide object that helps feed from events emitted in the journal
 */
class ChiefOfStateReadProcessor(
    actorSystem: ActorSystem,
    gRpcClient: HandlerServiceClient,
    handlerSetting: ChiefOfStateHandlerSetting
)(implicit ec: ExecutionContext)
    extends LagompbSlickProjection[State](actorSystem) {
  // $COVERAGE-OFF$

  override def aggregateStateCompanion: GeneratedMessageCompanion[State] = State

  override def projectionName: String = s"${LagompbConfig.serviceName}-readside-projection"

  // $COVERAGE-ON$

  override def handle(event: GeneratedMessage, state: State, metaData: MetaData): DBIO[Done] = {
    event match {
      case e: Event =>
        Try(
          gRpcClient.handleReadSide(
            HandleReadSideRequest()
              .withEvent(e.getEvent)
              .withState(state.getCurrentState)
              .withMeta(Any.pack(metaData))
          )
        ) match {
          case Failure(exception) =>
            log.error(s"[ChiefOfState]: unable to retrieve command handler response due to ${exception.getMessage}")
            DBIOAction.failed(throw new LagompbException(exception.getMessage))
          case Success(eventualReadSideResponse: Future[HandleReadSideResponse]) =>
            Try {
              Await.result(eventualReadSideResponse, Duration.Inf)
            } match {
              case Failure(exception) =>
                DBIOAction.failed(throw new LagompbException(s"[ChiefOfState]: ${exception.getMessage}"))
              case Success(value) =>
                if (value.successful) DBIOAction.successful(Done)
                else DBIOAction.failed(throw new LagompbException("[ChiefOfState]: unable to handle readSide"))
            }
        }
      case _ =>
        DBIOAction.failed(
          throw new LagompbException(s"[ChiefOfState]: event ${event.companion.scalaDescriptor.fullName} not handled")
        )
    }
  }
}
