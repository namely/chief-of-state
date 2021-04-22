/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, EntityRef }
import akka.util.Timeout
import com.google.protobuf.any
import com.namely.chiefofstate.config.WriteSideConfig
import com.namely.chiefofstate.plugin.PluginManager
import com.namely.chiefofstate.serialization.MessageWithActorRef
import com.namely.chiefofstate.telemetry.{ GrpcHeadersInterceptor, TracingHelpers }
import com.namely.protobuf.chiefofstate.v1.internal._
import com.namely.protobuf.chiefofstate.v1.internal.CommandReply.Reply
import com.namely.protobuf.chiefofstate.v1.persistence.StateWrapper
import com.namely.protobuf.chiefofstate.v1.service._
import io.grpc.{ Metadata, Status, StatusException }
import io.grpc.protobuf.StatusProto
import io.opentelemetry.context.Context
import org.slf4j.{ Logger, LoggerFactory }
import scalapb.GeneratedMessage

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }
import com.namely.protobuf.chiefofstate.v1.common.Header

class GrpcServiceImpl(clusterSharding: ClusterSharding, pluginManager: PluginManager, writeSideConfig: WriteSideConfig)(
    implicit val askTimeout: Timeout)
    extends ChiefOfStateServiceGrpc.ChiefOfStateService {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Used to process command sent by an application
   */
  override def processCommand(request: ProcessCommandRequest): Future[ProcessCommandResponse] = {
    log.debug(ChiefOfStateServiceGrpc.METHOD_PROCESS_COMMAND.getFullMethodName())

    val entityId: String = request.entityId

    // fetch the gRPC metadata
    val metadata: Metadata = GrpcHeadersInterceptor.REQUEST_META.get()

    val tracingHeaders = TracingHelpers.getTracingHeaders(Context.current())

    log.debug(s"Adding tracing headers to command $tracingHeaders")

    // ascertain the entity ID
    GrpcServiceImpl
      .requireEntityId(entityId)
      // run plugins to get meta
      .flatMap(_ => Future.fromTry(pluginManager.run(request, metadata)))
      // run remote command
      .flatMap(pluginData => {
        val entityRef: EntityRef[MessageWithActorRef] = clusterSharding.entityRefFor(AggregateRoot.TypeKey, entityId)

        val propagatedHeaders: Seq[Header] = Util.extractHeaders(metadata, writeSideConfig.propagatedHeaders)
        val persistedHeaders: Seq[Header] = Util.extractHeaders(metadata, writeSideConfig.persistedHeaders)

        val remoteCommand: RemoteCommand = RemoteCommand(
          command = request.command,
          propagatedHeaders = propagatedHeaders,
          persistedHeaders = persistedHeaders,
          data = pluginData)

        // ask entity for response to aggregate command
        entityRef ? ((replyTo: ActorRef[GeneratedMessage]) => {

          val sendCommand: SendCommand =
            SendCommand().withRemoteCommand(remoteCommand).withTracingHeaders(tracingHeaders)

          MessageWithActorRef(message = sendCommand, actorRef = replyTo)
        })
      })
      .map((msg: GeneratedMessage) => msg.asInstanceOf[CommandReply])
      .flatMap((value: CommandReply) => Future.fromTry(GrpcServiceImpl.handleCommandReply(value)))
      .map(c => ProcessCommandResponse().withState(c.getState).withMeta(c.getMeta))
  }

  /**
   * Used to get the current state of that entity
   */
  override def getState(request: GetStateRequest): Future[GetStateResponse] = {
    log.debug(ChiefOfStateServiceGrpc.METHOD_GET_STATE.getFullMethodName)

    val entityId: String = request.entityId

    val tracingHeaders = TracingHelpers.getTracingHeaders(Context.current())

    // ascertain the entity id
    GrpcServiceImpl
      .requireEntityId(entityId)
      .flatMap(_ => {
        val entityRef: EntityRef[MessageWithActorRef] = clusterSharding.entityRefFor(AggregateRoot.TypeKey, entityId)

        val getCommand = GetStateCommand().withEntityId(entityId)

        // ask entity for response to AggregateCommand
        entityRef ? ((replyTo: ActorRef[GeneratedMessage]) => {

          val sendCommand: SendCommand =
            SendCommand().withGetStateCommand(getCommand).withTracingHeaders(tracingHeaders)

          MessageWithActorRef(message = sendCommand, actorRef = replyTo)
        })
      })
      .map((msg: GeneratedMessage) => msg.asInstanceOf[CommandReply])
      .flatMap((value: CommandReply) => Future.fromTry(GrpcServiceImpl.handleCommandReply(value)))
      .map(c => GetStateResponse().withState(c.getState).withMeta(c.getMeta))
  }
}

object GrpcServiceImpl {

  /**
   * checks whether an entity ID is empty or not
   *
   * @param entityId the entity id
   * @return future for the validation
   */
  private[chiefofstate] def requireEntityId(entityId: String): Future[Unit] = {
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
  private[chiefofstate] def handleCommandReply(commandReply: CommandReply): Try[StateWrapper] = {
    commandReply.reply match {
      case Reply.State(value: StateWrapper) => Success(value)

      case Reply.Error(status: com.google.rpc.status.Status) =>
        val javaStatus: com.google.rpc.Status =
          com.google.rpc.Status.parseFrom(status.toByteArray)

        Failure(StatusProto.toStatusException(javaStatus))

      case default =>
        Failure(
          new StatusException(Status.INTERNAL.withDescription(s"unknown CommandReply ${default.getClass.getName}")))
    }
  }
}
