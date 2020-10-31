package com.namely.chiefofstate

import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.util.Timeout
import com.github.ghik.silencer.silent
import com.namely.chiefofstate.plugins.PluginManager
import com.namely.protobuf.chiefofstate.v1.internal.CommandReply.Reply
import com.namely.protobuf.chiefofstate.v1.internal.FailureResponse.FailureType
import com.namely.protobuf.chiefofstate.v1.internal._
import com.namely.protobuf.chiefofstate.v1.persistence.StateWrapper
import com.namely.protobuf.chiefofstate.v1.service.ChiefOfStateServiceGrpc.ChiefOfStateService
import com.namely.protobuf.chiefofstate.v1.service.{
  GetStateRequest,
  GetStateResponse,
  ProcessCommandRequest,
  ProcessCommandResponse
}
import io.grpc.{Metadata, Status, StatusException}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@silent
class GrpcServiceImpl(clusterSharding: ClusterSharding, pluginManager: PluginManager)(implicit val askTimeout: Timeout)
    extends ChiefOfStateService {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Used to process command sent by an application
   */
  override def processCommand(request: ProcessCommandRequest): Future[ProcessCommandResponse] = {
    val metadata: Metadata = GrpcHeadersInterceptor.REQUEST_META.get()
    val entityId: String = request.entityId
    val entityRef: EntityRef[AggregateCommand] = clusterSharding.entityRefFor(AggregateRoot.TypeKey, entityId)

    val reply: Future[CommandReply] =
      entityRef ? (replyTo =>
        AggregateCommand(
          SendCommand().withHandleCommand(
            HandleCommand().withCommand(request.getCommand).withEntityId(entityId)
          ),
          replyTo,
          Map.empty
        )
      )

    reply
      .flatMap((value: CommandReply) => Future.fromTry(handleCommandReply(value)))
      .map(c => ProcessCommandResponse().withState(c.getState).withMeta(c.getMeta))
  }

  /**
   * Used to get the current state of that entity
   */
  override def getState(request: GetStateRequest): Future[GetStateResponse] = {
    val metadata: Metadata = GrpcHeadersInterceptor.REQUEST_META.get()
    val entityId: String = request.entityId
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
