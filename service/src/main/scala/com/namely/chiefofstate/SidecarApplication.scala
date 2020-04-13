package com.namely.chiefofstate

import java.time.Duration

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import com.lightbend.lagom.scaladsl.akka.discovery.AkkaDiscoveryComponents
import com.lightbend.lagom.scaladsl.api.Descriptor
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.server.LagomApplication
import com.lightbend.lagom.scaladsl.server.LagomApplicationContext
import com.lightbend.lagom.scaladsl.server.LagomApplicationLoader
import com.lightbend.lagom.scaladsl.server.LagomServer
import com.namely.lagom.NamelyAggregate
import com.namely.lagom.NamelyLagomApplication
import com.namely.protobuf.chief_of_state.handler.HandlerService
import com.namely.protobuf.chief_of_state.handler.HandlerServiceClient
import com.softwaremill.macwire.wire

import scala.concurrent.ExecutionContextExecutor

abstract class SidecarApplication(context: LagomApplicationContext) extends NamelyLagomApplication(context) {

  override def aggregateRoot: NamelyAggregate[_] = SidecarAggregate

  override def server: LagomServer =
    serverFor[ChiefOfStateService](wire[SidecarServiceImpl])
      .additionalRouter(wire[SidecarGrpcServiceImpl])

  private implicit val dispatcher: ExecutionContextExecutor = actorSystem.dispatcher
  private implicit val sys: ActorSystem = actorSystem

  private lazy val settings = GrpcClientSettings
    .usingServiceDiscovery(HandlerService.name)
    .withServicePortName("https")
    // response timeout
    .withDeadline(Duration.ofSeconds(5))
    // use a small reconnectionAttempts value to
    // cause a client reload in case of failure
    .withConnectionAttempts(5)

  lazy val handlerServiceCLient: HandlerServiceClient = HandlerServiceClient(settings)
}

class SidecarApplicationLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new SidecarApplication(context) with AkkaDiscoveryComponents

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new SidecarApplication(context) with LagomDevModeComponents

  override def describeService: Option[Descriptor] = Some(readDescriptor[ChiefOfStateService])
}
