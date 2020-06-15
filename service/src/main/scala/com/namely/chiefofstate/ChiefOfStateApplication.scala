package com.namely.chiefofstate

import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.grpc.GrpcClientSettings
import com.lightbend.lagom.scaladsl.akka.discovery.AkkaDiscoveryComponents
import com.lightbend.lagom.scaladsl.api.Descriptor
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.server.{
  LagomApplication,
  LagomApplicationContext,
  LagomApplicationLoader,
  LagomServer
}
import com.namely.chiefofstate.api.ChiefOfStateService
import com.namely.protobuf.chief_of_state.handler.{HandlerService, HandlerServiceClient}
import com.namely.protobuf.chief_of_state.persistence.State
import com.softwaremill.macwire.wire
import lagompb.{LagompbAggregate, LagompbApplication, LagompbCommandHandler, LagompbEventHandler}

import scala.concurrent.ExecutionContextExecutor

/**
 * ChiefOfState application
 *
 * @param context
 */
abstract class ChiefOfStateApplication(context: LagomApplicationContext) extends LagompbApplication(context) {
  // $COVERAGE-OFF$

  implicit val sys: ActorSystem = actorSystem
  implicit val ec: ExecutionContextExecutor = sys.dispatcher

  // wiring up the grpc client
  lazy val handlerServiceClient: HandlerServiceClient = HandlerServiceClient(
    GrpcClientSettings.fromConfig(HandlerService.name)
  )
  // let us wire up the handler settings
  // this will break the application bootstrapping if the handler settings env variables are not set
  lazy val handlerSetting: ChiefOfStateHandlerSetting = ChiefOfStateHandlerSetting(config)

  //  Register a shutdown task to release resources of the client
  coordinatedShutdown
    .addTask(CoordinatedShutdown.PhaseServiceUnbind, "shutdown-handler-service-client") { () =>
      handlerServiceClient.close()
    }
  // wire up the various event and command handler
  lazy val eventHandler: LagompbEventHandler[State] = wire[ChiefOfStateEventHandler]
  lazy val commandHandler: LagompbCommandHandler[State] = wire[ChiefOfStateCommandHandler]
  lazy val aggregate: LagompbAggregate[State] = wire[ChiefOfStateAggregate]

  override def aggregateRoot: LagompbAggregate[_] = aggregate

  override def server: LagomServer =
    serverFor[ChiefOfStateService](wire[ChiefOfStateServiceImpl])
      .additionalRouter(wire[ChiefOfStateGrpcServiceImpl])

  if (config.getBoolean("chief-of-state.read-model.enabled")) {
    lazy val chiefOfStateReadProcessor: ChiefOfStateReadProcessor = wire[ChiefOfStateReadProcessor]
    chiefOfStateReadProcessor.init()
  }

  // $COVERAGE-ON$
}

/**
 * ChiefOfStateApplicationLoader boostraps the application at runtime
 */
class ChiefOfStateApplicationLoader extends LagomApplicationLoader {
  // $COVERAGE-OFF$
  override def load(context: LagomApplicationContext): LagomApplication =
    new ChiefOfStateApplication(context) with AkkaDiscoveryComponents

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new ChiefOfStateApplication(context) with LagomDevModeComponents

  override def describeService: Option[Descriptor] = Some(readDescriptor[ChiefOfStateService])

  // $COVERAGE-ON$
}
