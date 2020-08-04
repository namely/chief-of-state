package com.namely.chiefofstate

import akka.actor.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcServiceException
import akka.grpc.scaladsl.Metadata
import com.google.protobuf.any.Any
import com.namely.protobuf.chief_of_state.persistence.State
import com.namely.protobuf.chief_of_state.service._
import io.grpc.Status
import io.superflat.lagompb.{AggregateRoot, BaseGrpcServiceImpl, StateAndMeta}
import org.slf4j.{Logger, LoggerFactory}
import scalapb.{GeneratedMessage, GeneratedMessageCompanion}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

class GrpcServiceImpl(sys: ActorSystem, clusterSharding: ClusterSharding, aggregate: AggregateRoot[State])(implicit
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
      sendCommand[Any, State](clusterSharding, in.entityId, in.command.get, Map.empty[String, String])
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
