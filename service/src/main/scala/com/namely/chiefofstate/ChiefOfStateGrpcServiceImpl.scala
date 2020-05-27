package com.namely.chiefofstate

import akka.actor.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.scaladsl.Metadata
import com.google.protobuf.any.Any
import com.namely.lagom.{NamelyAggregate, NamelyGrpcServiceImpl, NamelyState}
import com.namely.protobuf.chief_of_state.persistence.State
import com.namely.protobuf.chief_of_state.service.{
  AbstractChiefOfStateServicePowerApiRouter,
  ProcessCommandRequest,
  ProcessCommandResponse
}
import scalapb.{GeneratedMessage, GeneratedMessageCompanion}

import scala.concurrent.{ExecutionContext, Future}

class ChiefOfStateGrpcServiceImpl(sys: ActorSystem, clusterSharding: ClusterSharding, aggregate: NamelyAggregate[State])(
    implicit ec: ExecutionContext
) extends AbstractChiefOfStateServicePowerApiRouter(sys)
    with NamelyGrpcServiceImpl {

  override def processCommand(in: ProcessCommandRequest, metadata: Metadata): Future[ProcessCommandResponse] = {

    //
    sendCommand[Any, State](clusterSharding, in.entityUuid, in.command.get, Map.empty[String, String])
      .map((namelyState: NamelyState[State]) => {
        ProcessCommandResponse()
          .withState(namelyState.state.getCurrentState)
          .withMeta(
            Any
              .pack(namelyState.eventMeta)
          )
      })
  }

  override def aggregateRoot: NamelyAggregate[_] = aggregate

  override def aggregateStateCompanion: GeneratedMessageCompanion[_ <: GeneratedMessage] = State
}
