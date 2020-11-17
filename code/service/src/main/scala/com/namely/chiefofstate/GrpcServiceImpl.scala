package com.namely.chiefofstate

import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.util.Timeout
import com.namely.chiefofstate.config.WriteSideConfig
import com.namely.chiefofstate.plugin.PluginManager
import com.namely.protobuf.chiefofstate.v1.internal._
import com.namely.protobuf.chiefofstate.v1.internal.CommandReply.Reply
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
import com.namely.chiefofstate.interceptors.GrpcHeadersInterceptor
import io.opentracing.util.GlobalTracer
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMap
import io.opentracing.Tracer
import io.opentracing.propagation.TextMapAdapter
import io.opentracing.propagation.TextMapInjectAdapter
import scala.jdk.CollectionConverters._
import scala.collection.mutable
import com.namely.protobuf.chiefofstate.v1.service.ChiefOfStateServiceGrpc
import com.namely.chiefofstate.interceptors.OpentracingHelpers
import io.opentracing.tag.Tags
import io.opentracing.Span

class GrpcServiceImpl(clusterSharding: ClusterSharding,
                      pluginManager: PluginManager,
                      writeSideConfig: WriteSideConfig,
                      tracer: Tracer
)(implicit
  val askTimeout: Timeout
) extends ChiefOfStateService {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Used to process command sent by an application
   */
  override def processCommand(request: ProcessCommandRequest): Future[ProcessCommandResponse] = {
    log.debug(ChiefOfStateServiceGrpc.METHOD_PROCESS_COMMAND.getFullMethodName())

    val entityId: String = request.entityId

    // fetch the gRPC metadata
    val metadata: Metadata = GrpcHeadersInterceptor.REQUEST_META.get()

    val tracingHeaders = OpentracingHelpers.getTracingHeaders(tracer)

    // ascertain the entity ID
    requireEntityId(entityId)
      // run plugins to get meta
      .flatMap(_ => Future.fromTry(pluginManager.run(request, metadata)))
      // run remote command
      .flatMap(meta => {
        val entityRef: EntityRef[AggregateCommand] = clusterSharding
          .entityRefFor(AggregateRoot.TypeKey, entityId)

        val remoteCommand: RemoteCommand = getRemoteCommand(writeSideConfig, request, metadata)
        val sendCommand: SendCommand = SendCommand()
          .withRemoteCommand(remoteCommand)
          .withTracingHeaders(tracingHeaders)

        // ask entity for response to aggregate command
        entityRef ? (replyTo => AggregateCommand(sendCommand, replyTo, meta))
      })
      .flatMap((value: CommandReply) => Future.fromTry(handleCommandReply(value)))
      .map(c => ProcessCommandResponse().withState(c.getState).withMeta(c.getMeta))
  }

  /**
   * Used to get the current state of that entity
   */
  override def getState(request: GetStateRequest): Future[GetStateResponse] = {
    log.debug(ChiefOfStateServiceGrpc.METHOD_GET_STATE.getFullMethodName())

    val entityId: String = request.entityId

    val tracingHeaders = OpentracingHelpers.getTracingHeaders(tracer)

    // ascertain the entity id
    requireEntityId(entityId)
      .flatMap(_ => {
        val entityRef: EntityRef[AggregateCommand] = clusterSharding
          .entityRefFor(AggregateRoot.TypeKey, entityId)

        val getCommand = GetStateCommand().withEntityId(entityId)

        val sendCommand = SendCommand()
          .withGetStateCommand(getCommand)
          .withTracingHeaders(tracingHeaders)

        // ask entity for response to AggregateCommand
        entityRef ? (replyTo => AggregateCommand(sendCommand, replyTo, Map.empty))
      })
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
      .transformMetadataToRemoteCommandHeader(metadata, writeSideConfig.propagatedHeaders)

    RemoteCommand(
      command = processCommandRequest.command,
      headers = propagatedHeaders
    )
  }

  /**
   * checks whether an entity ID is empty or not
   *
   * @param entityId the entity id
   * @return future for the validation
   */
  private[this] def requireEntityId(entityId: String): Future[Unit] = {
    if (entityId.isEmpty) {
      Future.failed(new StatusException(Status.INVALID_ARGUMENT.withDescription("empty entity ID")))
    } else {
      Future.successful {}
    }
  }

  /**
   * handles the command reply, specifically for errors, and
   * reports errors to the global tracer
   *
   * @param commandReply a command reply
   * @return a state wrapper
   */
  private[this] def handleCommandReply(commandReply: CommandReply): Try[StateWrapper] = {
    commandReply.reply match {
      case Reply.State(value: StateWrapper) => Success(value)

      case Reply.Error(status: com.google.rpc.status.Status) =>
        Failure(
          new StatusException(
            io.grpc.Status
              .fromCodeValue(status.code)
              .withDescription(status.message)
          )
        )

      case default =>
        Failure(
          new StatusException(
            Status.INTERNAL.withDescription(s"unknown CommandReply ${default.getClass.getName}")
          )
        )
    }
  }
}
