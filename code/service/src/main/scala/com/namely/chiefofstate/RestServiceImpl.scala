package com.namely.chiefofstate

import akka.NotUsed
import akka.actor.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding

import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.scaladsl.api.{Descriptor, ServiceCall}
import com.lightbend.lagom.scaladsl.api.Service.restCall
import com.lightbend.lagom.scaladsl.api.transport.Method

import com.google.protobuf.any.Any

import io.superflat.lagompb.{AggregateRoot, BaseServiceImpl}
import io.superflat.lagompb.BaseService

import scalapb.{GeneratedMessage, GeneratedMessageCompanion}

import scala.concurrent.{ExecutionContext, Future}

class RestServiceImpl(
  actorSystem: ActorSystem,
  clusterSharding: ClusterSharding,
  persistentEntityRegistry: PersistentEntityRegistry,
  aggregateRoot: AggregateRoot
)
    extends BaseServiceImpl(clusterSharding, persistentEntityRegistry, aggregateRoot)
    with ChiefOfStateService {

  override def handleCommand(): ServiceCall[NotUsed, String] =
    ServiceCall { _ =>
      Future.successful("Welcome to Chief Of State. The gRPC distributed event sourcing application!!!")
    }

  // TODO: Deprecate this!
  def aggregateStateCompanion: GeneratedMessageCompanion[_ <: scalapb.GeneratedMessage] = Any
}

trait ChiefOfStateService extends BaseService {

  def handleCommand(): ServiceCall[NotUsed, String]

  override val routes: Seq[Descriptor.Call[_, _]] = Seq(restCall(Method.GET, "/", handleCommand _))
}
