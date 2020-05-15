package com.namely.chiefofstate

import akka.NotUsed
import akka.actor.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.namely.chiefofstate.api.ChiefOfStateService
import com.namely.lagom.NamelyAggregate
import com.namely.lagom.NamelyServiceImpl
import com.namely.protobuf.chief_of_state.persistence.State
import scalapb.GeneratedMessage
import scalapb.GeneratedMessageCompanion

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ChiefOfStateServiceImpl(
    actorSystem: ActorSystem,
    clusterSharding: ClusterSharding,
    persistentEntityRegistry: PersistentEntityRegistry,
    aggregate: NamelyAggregate[State]
)(
    implicit ec: ExecutionContext
) extends NamelyServiceImpl(clusterSharding, persistentEntityRegistry, aggregate)
    with ChiefOfStateService {

  override def handleCommand(): ServiceCall[NotUsed, String] = ServiceCall { _ =>
    Future.successful("Welcome Chief Of State...")
  }

  override def aggregateStateCompanion: GeneratedMessageCompanion[_ <: GeneratedMessage] = State
}
