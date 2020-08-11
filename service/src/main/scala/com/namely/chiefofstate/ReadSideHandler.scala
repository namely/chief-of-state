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
import io.superflat.lagompb.encryption.EncryptionAdapter
import io.superflat.lagompb.readside.{ReadSideEvent, ReadSideProcessor}
import scalapb.GeneratedMessageCompanion
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
  encryptionAdapter: EncryptionAdapter,
  actorSystem: ActorSystem,
  readSideHandlerServiceClient: ReadSideHandlerServiceClient,
  handlerSetting: HandlerSetting
)(implicit ec: ExecutionContext)
    extends ReadSideProcessor[State](encryptionAdapter)(ec, actorSystem.toTyped) {
  // $COVERAGE-OFF$

  override def aggregateStateCompanion: GeneratedMessageCompanion[State] = State

  override def projectionName: String =
    s"${grpcReadSideConfig.processorId}-${ConfigReader.serviceName}-readside-projection"

  // $COVERAGE-ON$

  private val COS_EVENT_TAG_HEADER = "x-cos-event-tag"
  private val COS_ENTITY_ID_HEADER = "x-cos-entity-id"

  override def handle(readSideEvent: ReadSideEvent[State]): DBIO[Done] = {

    readSideEvent.event match {
      case e: Event =>
        Try(
          readSideHandlerServiceClient
            .handleReadSide()
            .addHeader(COS_ENTITY_ID_HEADER, readSideEvent.metaData.entityId)
            .addHeader(COS_EVENT_TAG_HEADER, readSideEvent.eventTag)
            .invoke(
              HandleReadSideRequest()
                .withEvent(e.getEvent)
                .withState(readSideEvent.state.getCurrentState)
                .withMeta(
                  common
                    .MetaData()
                    .withEntityId(readSideEvent.metaData.entityId)
                    .withRevisionNumber(readSideEvent.metaData.revisionNumber)
                    .withRevisionDate(readSideEvent.metaData.getRevisionDate)
                    .withData(readSideEvent.metaData.data)
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
            s"[ChiefOfState]: ${grpcReadSideConfig.processorId} - event ${readSideEvent.event.companion.scalaDescriptor.fullName} not handled"
          )
        )
    }
  }
}
