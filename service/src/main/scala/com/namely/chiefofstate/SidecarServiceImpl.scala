package com.namely.chiefofstate

import akka.NotUsed
import akka.actor.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.google.protobuf.any.Any
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.namely.lagom.NamelyAggregate
import com.namely.lagom.NamelyServiceImpl
import scalapb.GeneratedMessage
import scalapb.GeneratedMessageCompanion

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class SidecarServiceImpl(actorSystem: ActorSystem, clusterSharding: ClusterSharding, persistentEntityRegistry: PersistentEntityRegistry)(
    implicit ec: ExecutionContext
) extends NamelyServiceImpl(clusterSharding, persistentEntityRegistry)
    with ChiefOfStateService {

  override def handleCommand(): ServiceCall[NotUsed, String] = ServiceCall { _ =>
    Future.successful("")
  }

  override def aggregateRoot: NamelyAggregate[_] = new SidecarAggregate(actorSystem)

  override def aggregateStateCompanion: GeneratedMessageCompanion[_ <: GeneratedMessage] = Any
}
