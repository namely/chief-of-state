package com.namely.chiefofstate

import akka.NotUsed
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.namely.lagom.{NamelyAggregate, NamelyServiceImpl}
import scalapb.{GeneratedMessage, GeneratedMessageCompanion}
import com.google.protobuf.any.Any

import scala.concurrent.{ExecutionContext, Future}

class SidecarServiceImpl(clusterSharding: ClusterSharding, persistentEntityRegistry: PersistentEntityRegistry)(
  implicit ec: ExecutionContext
) extends NamelyServiceImpl(clusterSharding, persistentEntityRegistry)
  with ChiefOfStateService {

  override def handleCommand(): ServiceCall[NotUsed, String] = ServiceCall {
    _ => Future.successful("")

  }

  override def aggregateRoot: NamelyAggregate[_] = SidecarAggregate

  override def aggregateStateCompanion: GeneratedMessageCompanion[_ <: GeneratedMessage] = Any
}
