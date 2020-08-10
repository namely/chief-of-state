package com.namely.chiefofstate

import akka.actor.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcServiceException
import akka.grpc.scaladsl.{BytesEntry, StringEntry, Metadata}
import com.google.protobuf.any.Any
import com.namely.protobuf.chief_of_state.persistence.State
import com.namely.protobuf.chief_of_state.service.{
  ProcessCommandRequest,
  ProcessCommandResponse,
  GetStateRequest,
  GetStateResponse,
  AbstractChiefOfStateServicePowerApiRouter
}
import io.grpc.Status
import io.superflat.lagompb.{AggregateRoot, BaseGrpcServiceImpl, StateAndMeta}
import org.slf4j.{Logger, LoggerFactory}
import scalapb.{GeneratedMessage, GeneratedMessageCompanion}
import com.namely.protobuf.chief_of_state.internal.RemoteCommand
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure
import com.google.protobuf.ByteString

class GrpcServiceImpl(
  sys: ActorSystem,
  clusterSharding: ClusterSharding,
  aggregate: AggregateRoot[State],
  sendCommandSettings: SendCommandSettings)(implicit
  ec: ExecutionContext
) extends AbstractChiefOfStateServicePowerApiRouter(sys)
    with BaseGrpcServiceImpl {

  private val log: Logger = LoggerFactory.getLogger(getClass)

  override def aggregateRoot: AggregateRoot[_] = aggregate

  override def aggregateStateCompanion: GeneratedMessageCompanion[_ <: GeneratedMessage] = State

  /**
   * gRPC ProcessCommand implementation
   *
   * @param in the ProcessCommandRequest
   * @param metadata akka gRPC metadata
   * @return future with the command response
   */
  override def processCommand(in: ProcessCommandRequest, metadata: Metadata): Future[ProcessCommandResponse] = {

    if (in.entityId.isEmpty()) {
      val status = Status.INVALID_ARGUMENT.withDescription("empty entity ID")
      val e: Throwable = new GrpcServiceException(status = status)
      log.error(s"request missing entity id")
      Future.fromTry(Failure(e))

    } else {

      // TODO: move this to a general plugin architecture
      val metaData: Map[String, String] = {
        // get the headers to persist
        val persistedHeaders: Map[String, String] =
          sendCommandSettings
          .persistedHeaders
          .map(s => (s, metadata.getText(s)))
          .filter({case(_, value) => value.isDefined})
          .map({case (k, optValue) => (s"grpcHeader|$k", optValue.getOrElse(""))})
          .toMap

        persistedHeaders
      }

      // get the headers to forward
      val propagatedHeaders: Seq[RemoteCommand.Header] = metadata
        .asList
        // filter to relevant headers
        .filter({case (k, _) => sendCommandSettings.propagatedHeaders.contains(k)})
        .map({
          case (k, StringEntry(value)) =>
            RemoteCommand.Header()
              .withKey(k)
              .withStringValue(value)

          case (k, BytesEntry(value)) =>
            RemoteCommand.Header()
              .withKey(k)
              .withBytesValue(ByteString.copyFrom(value.toArray))
        })

      val remoteCommand: RemoteCommand = RemoteCommand()
        .withCommand(in.getCommand)
        .withHeaders(propagatedHeaders)

      sendCommand[RemoteCommand, State](clusterSharding, in.entityId, remoteCommand, metaData)
        .map((namelyState: StateAndMeta[State]) => {
          ProcessCommandResponse(
            state = namelyState.state.currentState,
            meta = Some(Util.toCosMetaData(namelyState.metaData))
          )
        })
    }
  }

  /**
   * gRPC GetState implementation
   *
   * @param in GetStateRequest
   * @param metadata akka gRPC metadata
   * @return future of GetStateResponse
   */
  override def getState(in: GetStateRequest, metadata: Metadata): Future[GetStateResponse] = {
    if (in.entityId.isEmpty()) {
      val status = Status.INVALID_ARGUMENT.withDescription("empty entity ID")
      val e: Throwable = new GrpcServiceException(status = status)
      log.error(s"request missing entity id")
      Future.fromTry(Failure(e))

    } else {
      sendCommand[GetStateRequest, State](clusterSharding, in.entityId, in, Map.empty[String, String])
        .map((namelyState: StateAndMeta[State]) => {
          GetStateResponse(
            state = namelyState.state.currentState,
            meta = Some(Util.toCosMetaData(namelyState.metaData))
          )
        })
    }
  }
}
