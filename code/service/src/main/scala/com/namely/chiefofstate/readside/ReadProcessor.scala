package com.namely.chiefofstate.readside

import akka.Done
import akka.actor.{typed, ActorSystem}
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.{ProjectionBehavior, ProjectionId}
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.{ExactlyOnceProjection, SourceProvider}
import akka.projection.slick.SlickProjection
import com.github.ghik.silencer.silent
import com.namely.chiefofstate.config.ReadSideSetting
import com.namely.chiefofstate.grpc.client.ReadSideHandlerServiceClient
import com.namely.protobuf.chiefofstate.v1.common.MetaData
import com.namely.protobuf.chiefofstate.v1.persistence.EventWrapper
import com.namely.protobuf.chiefofstate.v1.readside.{HandleReadSideRequest, HandleReadSideResponse}
import io.superflat.lagompb.encryption.EncryptionAdapter
import io.superflat.lagompb.ConfigReader
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.dbio.{DBIO, DBIOAction}
import slick.jdbc.PostgresProfile

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

/**
 * Handles events consumed from the journal by making them available to any readside handler
 *
 * @param grpcReadSideConfig the readside handler grpc client config
 * @param encryptionAdapter the encryption adapter
 * @param actorSystem the actor system
 * @param readSideHandlerServiceClient the readSidehandlerServiceClient
 * @param ec the execution context
 */
@silent
class ReadProcessor(
  grpcReadSideConfig: ReadSideSetting,
  encryptionAdapter: EncryptionAdapter,
  actorSystem: ActorSystem,
  readSideHandlerServiceClient: ReadSideHandlerServiceClient
)(implicit
  ec: ExecutionContext
) extends EventsProcessor {

  implicit val typedActorSys: typed.ActorSystem[_] = actorSystem.toTyped

  final val log: Logger = LoggerFactory.getLogger(getClass)

  // The implementation class needs to set the akka.projection.slick config for the offset database
  protected val offsetStoreDatabaseConfig: DatabaseConfig[PostgresProfile] =
    DatabaseConfig.forConfig("akka.projection.slick", actorSystem.settings.config)

  protected val baseTag: String = ConfigReader.eventsConfig.tagName

  def projectionName: String =
    s"${grpcReadSideConfig.processorId}-${ConfigReader.serviceName}-readside-projection"

  private val COS_EVENT_TAG_HEADER = "x-cos-event-tag"
  private val COS_ENTITY_ID_HEADER = "x-cos-entity-id"

  /**
   * Processes events read from the Journal
   *
   * @param event          the actual event
   * @param eventTag       the event tag
   * @param resultingState the resulting state of the applied event
   * @param meta           the additional meta data
   * @return
   */
  override def process(event: com.google.protobuf.any.Any,
                       eventTag: String,
                       resultingState: com.google.protobuf.any.Any,
                       meta: MetaData
  ): DBIO[Done] = {
    val eventualResponse: Try[HandleReadSideResponse] = Try {
      val futureResponse: Future[HandleReadSideResponse] = readSideHandlerServiceClient
        .handleReadSide()
        .addHeader(COS_ENTITY_ID_HEADER, meta.entityId)
        .addHeader(COS_EVENT_TAG_HEADER, eventTag)
        .invoke(
          HandleReadSideRequest()
            .withEvent(event)
            .withState(resultingState)
            .withMeta(meta)
        )

      Await.result(futureResponse, Duration.Inf)
    }

    eventualResponse match {
      case Failure(exception) =>
        log.error(
          s"[ChiefOfState]: ${grpcReadSideConfig.processorId} - unable to retrieve command handler response due to ${exception.getMessage}"
        )
        DBIOAction.failed(exception)
      case Success(value) => handleSuccessfulResponse(value)
    }
  }

  /**
   * Initialize the projection to start fetching the events that are emitted
   */
  def start(): Unit = {
    // Let us attempt to create the projection store
    if (ConfigReader.createOffsetStore) SlickProjection.createOffsetTableIfNotExists(offsetStoreDatabaseConfig)

    ShardedDaemonProcess(typedActorSys).init[ProjectionBehavior.Command](
      name = projectionName,
      numberOfInstances = ConfigReader.allEventTags.size,
      behaviorFactory = n => ProjectionBehavior(exactlyOnceProjection(s"$baseTag$n")),
      settings = ShardedDaemonProcessSettings(typedActorSys),
      stopMessage = Some(ProjectionBehavior.Stop)
    )
  }

  /**
   * Build the projection instance based upon the event tag
   *
   * @param tagName the event tag
   * @return the projection instance
   */
  protected def exactlyOnceProjection(tagName: String): ExactlyOnceProjection[Offset, EventEnvelope[EventWrapper]] = {
    SlickProjection
      .exactlyOnce(
        projectionId = ProjectionId(projectionName, tagName),
        sourceProvider(tagName),
        offsetStoreDatabaseConfig,
        handler = () => new EventsConsumer(tagName, encryptionAdapter, this)
      )
  }

  /**
   * Set the Event Sourced Provider per tag
   *
   * @param tag the event tag
   * @return the event sourced provider
   */
  protected def sourceProvider(tag: String): SourceProvider[Offset, EventEnvelope[EventWrapper]] = {
    EventSourcedProvider
      .eventsByTag[EventWrapper](typedActorSys, readJournalPluginId = JdbcReadJournal.Identifier, tag)
  }

  private[this] def handleSuccessfulResponse(readSideResponse: HandleReadSideResponse) = {
    if (readSideResponse.successful) DBIOAction.successful(Done)
    else
      DBIOAction.failed(
        new Exception(
          s"[ChiefOfState]: ${grpcReadSideConfig.processorId} - unable to handle readSide"
        )
      )
  }

}
