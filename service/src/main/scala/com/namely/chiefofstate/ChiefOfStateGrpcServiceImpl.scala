package com.namely.chiefofstate

import akka.actor.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.scaladsl.Metadata
import com.google.protobuf.any.Any
import com.namely.protobuf.chief_of_state.cos_common
import com.namely.protobuf.chief_of_state.cos_persistence.State
import com.namely.protobuf.chief_of_state.cos_service.{
  AbstractChiefOfStateServicePowerApiRouter,
  ProcessCommandRequest,
  ProcessCommandResponse
}
import io.superflat.lagompb.{AggregateRoot, BaseGrpcServiceImpl, StateAndMeta}
import scalapb.{GeneratedMessage, GeneratedMessageCompanion}

import scala.concurrent.{ExecutionContext, Future}

class ChiefOfStateGrpcServiceImpl(sys: ActorSystem, clusterSharding: ClusterSharding, aggregate: AggregateRoot[State])(
    implicit ec: ExecutionContext
) extends AbstractChiefOfStateServicePowerApiRouter(sys)
    with BaseGrpcServiceImpl {

  override def aggregateRoot: AggregateRoot[_] = aggregate

  override def aggregateStateCompanion: GeneratedMessageCompanion[_ <: GeneratedMessage] = State

  override def processCommand(in: ProcessCommandRequest, metadata: Metadata): Future[ProcessCommandResponse] = {

    sendCommand[Any, State](clusterSharding, in.entityUuid, in.command.get, Map.empty[String, String])
      .map((namelyState: StateAndMeta[State]) => {
        ProcessCommandResponse()
          .withState(namelyState.state.getCurrentState)
          .withMeta(
            cos_common
              .MetaData()
              .withData(namelyState.metaData.data)
              .withRevisionDate(namelyState.metaData.getRevisionDate)
              .withRevisionNumber(namelyState.metaData.revisionNumber)
          )
      })
  }

}
