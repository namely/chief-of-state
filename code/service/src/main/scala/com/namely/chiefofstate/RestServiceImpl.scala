package com.namely.chiefofstate

import akka.NotUsed
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.lightbend.lagom.scaladsl.api.Service.restCall
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Descriptor, ServiceCall}
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import io.superflat.lagompb.{AggregateRoot, BaseService, BaseServiceImpl}

import scala.concurrent.Future

class RestServiceImpl(
  clusterSharding: ClusterSharding,
  persistentEntityRegistry: PersistentEntityRegistry,
  aggregateRoot: AggregateRoot
) extends BaseServiceImpl(clusterSharding, persistentEntityRegistry, aggregateRoot)
    with ChiefOfStateService {

  override def handleCommand(): ServiceCall[NotUsed, String] =
    ServiceCall { _ =>
      Future.successful("Welcome to Chief Of State. The gRPC distributed event sourcing application!!!")
    }
}

trait ChiefOfStateService extends BaseService {

  def handleCommand(): ServiceCall[NotUsed, String]

  override val routes: Seq[Descriptor.Call[_, _]] = Seq(restCall(Method.GET, "/", handleCommand _))
}
