package com.namely.chiefofstate

import akka.actor.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.scaladsl.Metadata
import com.google.protobuf.any.Any
import com.namely.lagom.NamelyAggregate
import com.namely.lagom.NamelyGrpcServiceImpl
import com.namely.protobuf.chief_of_state.service.AbstractChiefOfStateServicePowerApiRouter
import com.namely.protobuf.chief_of_state.service.ProcessCommandRequest
import com.namely.protobuf.chief_of_state.service.ProcessCommandResponse
import scalapb.GeneratedMessage
import scalapb.GeneratedMessageCompanion

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class SidecarGrpcServiceImpl(sys: ActorSystem, clusterSharding: ClusterSharding)(implicit ec: ExecutionContext)
    extends AbstractChiefOfStateServicePowerApiRouter(sys)
    with NamelyGrpcServiceImpl {

  override def processCommand(in: ProcessCommandRequest, metadata: Metadata): Future[ProcessCommandResponse] = {

    sendCommand[Any, Any](clusterSharding, in.entityUuid, in.command.get).map(
      state => ProcessCommandResponse().withState(state.state).withMeta(Any.pack(state.meta))
    )
  }

  override def aggregateRoot: NamelyAggregate[_] = SidecarAggregate

  override def aggregateStateCompanion: GeneratedMessageCompanion[_ <: GeneratedMessage] = Any
}
