package com.namely.chiefofstate

import akka.NotUsed
import akka.actor.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.namely.chiefofstate.api.ChiefOfStateService
import com.google.protobuf.any.Any
import io.superflat.lagompb.{AggregateRoot, BaseServiceImpl}
import scalapb.{GeneratedMessage, GeneratedMessageCompanion}

import scala.concurrent.{ExecutionContext, Future}

class RestServiceImpl(
  actorSystem: ActorSystem,
  clusterSharding: ClusterSharding,
  persistentEntityRegistry: PersistentEntityRegistry,
  aggregate: Aggregate
)(implicit ec: ExecutionContext)
    extends BaseServiceImpl(clusterSharding, persistentEntityRegistry, aggregate)
    with ChiefOfStateService {

  override def handleCommand(): ServiceCall[NotUsed, String] =
    ServiceCall { _ =>
      Future.successful("Welcome to Chief Of State. The gRPC distributed event sourcing application!!!")
    }

  // TODO: Deprecate this!
  def aggregateStateCompanion: GeneratedMessageCompanion[_ <: scalapb.GeneratedMessage] = Any
}
