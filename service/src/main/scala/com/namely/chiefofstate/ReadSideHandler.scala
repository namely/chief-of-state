package com.namely.chiefofstate

import akka.Done
import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import com.namely.protobuf.chief_of_state.common
import com.namely.protobuf.chief_of_state.persistence.{Event, State}
import com.namely.protobuf.chief_of_state.readside.{
  HandleReadSideRequest,
  HandleReadSideResponse,
  ReadSideHandlerServiceClient
}
import io.superflat.lagompb.{ConfigReader, GlobalException}
import io.superflat.lagompb.encryption.ProtoEncryption
import io.superflat.lagompb.protobuf.core.MetaData
import io.superflat.lagompb.readside.ReadSideProcessor
import scalapb.{GeneratedMessage, GeneratedMessageCompanion}
import slick.dbio.{DBIO, DBIOAction}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

/**
 * ChiefOfStateReadProcessor
 *
 * @param actorSystem                  the actor system
 * @param readSideHandlerServiceClient the gRpcClient used to connect to the actual readSide handler
 * @param handlerSetting               the readSide handler settingthe lagom readSide object that helps feed from events emitted in the journal
 */
class ReadSideHandler(
    grpcReadSideConfig: ReadSideConfig,
    encryption: ProtoEncryption,
    actorSystem: ActorSystem,
    readSideHandlerServiceClient: ReadSideHandlerServiceClient,
    handlerSetting: HandlerSetting
)(implicit ec: ExecutionContext)
    extends ReadSideProcessor[State](encryption)(ec, actorSystem.toTyped) {
  // $COVERAGE-OFF$

  override def aggregateStateCompanion: GeneratedMessageCompanion[State] = State

  override def projectionName: String =
    s"${grpcReadSideConfig.processorId}-${ConfigReader.serviceName}-readside-projection"

  // $COVERAGE-ON$

  override def handle(event: GeneratedMessage, state: State, metaData: MetaData): DBIO[Done] = {
    event match {
      case e: Event =>
        Try(
          readSideHandlerServiceClient.handleReadSide(
            HandleReadSideRequest()
              .withEvent(e.getEvent)
              .withState(state.getCurrentState)
              .withMeta(
                common
                  .MetaData()
                  .withData(metaData.data)
                  .withRevisionDate(metaData.getRevisionDate)
                  .withRevisionNumber(metaData.revisionNumber)
              )
          )
        ) match {
          case Failure(exception) =>
            log.error(
              s"[ChiefOfState]: ${grpcReadSideConfig.processorId} - unable to retrieve command handler response due to ${exception.getMessage}"
            )
            DBIOAction.failed(throw new GlobalException(exception.getMessage))
          case Success(eventualReadSideResponse: Future[HandleReadSideResponse]) =>
            Try {
              Await.result(eventualReadSideResponse, Duration.Inf)
            } match {
              case Failure(exception) =>
                DBIOAction.failed(
                  throw new GlobalException(
                    s"[ChiefOfState]: ${grpcReadSideConfig.processorId} - ${exception.getMessage}"
                  )
                )
              case Success(value) =>
                if (value.successful) DBIOAction.successful(Done)
                else
                  DBIOAction.failed(
                    throw new GlobalException(
                      s"[ChiefOfState]: ${grpcReadSideConfig.processorId} - unable to handle readSide"
                    )
                  )
            }
        }
      case _ =>
        DBIOAction.failed(
          throw new GlobalException(
            s"[ChiefOfState]: ${grpcReadSideConfig.processorId} - event ${event.companion.scalaDescriptor.fullName} not handled"
          )
        )
    }
  }
}
