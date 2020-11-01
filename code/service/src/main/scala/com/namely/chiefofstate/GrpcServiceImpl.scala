package com.namely.chiefofstate

import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.util.Timeout
import com.github.ghik.silencer.silent
import com.google.protobuf.any
import com.namely.chiefofstate.config.WriteSideConfig
import com.namely.chiefofstate.plugin.PluginManager
import com.namely.protobuf.chiefofstate.v1.internal._
import com.namely.protobuf.chiefofstate.v1.internal.CommandReply.Reply
import com.namely.protobuf.chiefofstate.v1.internal.FailureResponse.FailureType
import com.namely.protobuf.chiefofstate.v1.persistence.StateWrapper
import com.namely.protobuf.chiefofstate.v1.service.{
  GetStateRequest,
  GetStateResponse,
  ProcessCommandRequest,
  ProcessCommandResponse
}
import com.namely.protobuf.chiefofstate.v1.service.ChiefOfStateServiceGrpc.ChiefOfStateService
import io.grpc.{Metadata, Status, StatusException}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@silent
class GrpcServiceImpl(clusterSharding: ClusterSharding, pluginManager: PluginManager, writeSideConfig: WriteSideConfig)(
  implicit val askTimeout: Timeout
) extends ChiefOfStateService {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Used to process command sent by an application
   */
  override def processCommand(request: ProcessCommandRequest): Future[ProcessCommandResponse] = {
    val entityId: String = request.entityId

    // ascertain the entity ID
    requireEntityId(entityId)

    // fetch the gRPC metadata
    val metadata: Metadata = GrpcHeadersInterceptor.REQUEST_META.get()

    // run plugins to get meta
    Future
      .fromTry(pluginManager.run(request, metadata))
      // run remote command
      .flatMap(meta => {
        val remoteCommand: RemoteCommand = getRemoteCommand(writeSideConfig, request, metadata)
        val entityRef: EntityRef[AggregateCommand] = clusterSharding.entityRefFor(AggregateRoot.TypeKey, entityId)

        val sendCommand: SendCommand = SendCommand()
          .withHandleCommand(
            HandleCommand()
              .withEntityId(entityId)
              .withCommand(remoteCommand)
          )

        entityRef ? (replyTo => AggregateCommand(sendCommand, replyTo, meta))
      })
      .flatMap((value: CommandReply) => Future.fromTry(handleCommandReply(value)))
      .map(c => ProcessCommandResponse().withState(c.getState).withMeta(c.getMeta))
  }

  /**
   * Used to get the current state of that entity
   */
  override def getState(request: GetStateRequest): Future[GetStateResponse] = {
    val entityId: String = request.entityId
    requireEntityId(entityId)

    val metadata: Metadata = GrpcHeadersInterceptor.REQUEST_META.get()

    val entityRef: EntityRef[AggregateCommand] = clusterSharding.entityRefFor(AggregateRoot.TypeKey, entityId)
    val reply: Future[CommandReply] =
      entityRef ? (replyTo =>
        AggregateCommand(
          SendCommand().withGetStateCommand(GetStateCommand().withEntityId(entityId)),
          replyTo,
          Map.empty
        )
      )

    reply
      .flatMap((value: CommandReply) => Future.fromTry(handleCommandReply(value)))
      .map(c => GetStateResponse().withState(c.getState).withMeta(c.getMeta))
  }

  /**
   * builds the remote command to execute
   *
   * @param writeSideConfig the write side config instance
   * @param processCommandRequest the initial request
   * @param metadata the gRPC metadata
   * @return the RemoteCommand object
   */
  private[this] def getRemoteCommand(writeSideConfig: WriteSideConfig,
                                     processCommandRequest: ProcessCommandRequest,
                                     metadata: Metadata
  ): RemoteCommand = {
    // get the headers to forward
    val propagatedHeaders: Seq[RemoteCommand.Header] = Util
      .transformMetadataToRemoteCommandHeader(metadata)
      .filter(rc => {
        writeSideConfig.propagatedHeaders.contains(rc.key)
      })

    RemoteCommand()
      .withCommand(processCommandRequest.getCommand)
      .withHeaders(propagatedHeaders)
  }

  /**
   * checks whether an entity ID is empty or not
   *
   * @param entityId the entity id
   */
  private[this] def requireEntityId(entityId: String): Unit = {
    if (entityId.isEmpty)
      throw new StatusException(Status.INVALID_ARGUMENT.withDescription("empty entity ID"))
  }

  /**
   * transforms the failure response and return a gRPC exception
   *
   * @param failureResponse the failure response
   * @return the gRPC exception returned
   */
  private[this] def transformFailure(failureResponse: FailureResponse): Failure[Throwable] = {
    failureResponse.failureType match {
      case FailureType.Critical(value) =>
        Failure(
          new StatusException(Status.INTERNAL.withDescription(value))
        )

      case FailureType.Custom(value) =>
        Failure(
          new StatusException(Status.INTERNAL.withDescription(s"unhandled custom error: ${value.typeUrl}"))
        )

      case FailureType.Validation(value) =>
        Failure(
          new StatusException(Status.INVALID_ARGUMENT.withDescription(value))
        )

      case FailureType.NotFound(value) =>
        Failure(
          new StatusException(
            Status.NOT_FOUND.withDescription(value)
          )
        )

      case FailureType.Empty =>
        Failure(
          new StatusException(
            Status.INTERNAL.withDescription("unknown failure type")
          )
        )
    }
  }

  private[this] def handleCommandReply(commandReply: CommandReply): Try[StateWrapper] = {
    commandReply.reply match {
      case Reply.Empty =>
        Failure(
          new StatusException(
            Status.INTERNAL.withDescription(s"unknown CommandReply ${commandReply.reply.getClass.getName}")
          )
        )
      case Reply.State(value: StateWrapper) => Success(value)
      case Reply.Failure(value)             => transformFailure(value).asInstanceOf[Try[StateWrapper]]
    }
  }
}
