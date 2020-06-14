package com.namely.chiefofstate

import akka.actor.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.scaladsl.Metadata
import com.google.protobuf.any.Any
import com.namely.protobuf.chief_of_state.persistence.State
import com.namely.protobuf.chief_of_state.service.{
  AbstractChiefOfStateServicePowerApiRouter,
  ProcessCommandRequest,
  ProcessCommandResponse
}
import lagompb.{LagompbAggregate, LagompbGrpcServiceImpl, LagompbState}
import scalapb.{GeneratedMessage, GeneratedMessageCompanion}

import scala.concurrent.{ExecutionContext, Future}

class ChiefOfStateGrpcServiceImpl(
    sys: ActorSystem,
    clusterSharding: ClusterSharding,
    aggregate: LagompbAggregate[State]
)(implicit ec: ExecutionContext)
    extends AbstractChiefOfStateServicePowerApiRouter(sys)
    with LagompbGrpcServiceImpl {

  override def aggregateRoot: LagompbAggregate[_] = aggregate

  override def aggregateStateCompanion: GeneratedMessageCompanion[_ <: GeneratedMessage] = State

  override def processCommand(in: ProcessCommandRequest, metadata: Metadata): Future[ProcessCommandResponse] = {

    sendCommand[Any, State](clusterSharding, in.entityUuid, in.command.get, Map.empty[String, String])
      .map((namelyState: LagompbState[State]) => {
        ProcessCommandResponse()
          .withState(namelyState.state.getCurrentState)
          .withMeta(
            Any
              .pack(namelyState.metaData)
          )
      })
  }

}
