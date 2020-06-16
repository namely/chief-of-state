package com.namely.chiefofstate

import akka.actor.CoordinatedShutdown
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
import com.namely.protobuf.chief_of_state.persistence.State
import com.namely.protobuf.chief_of_state.readside_handler.ReadSideHandlerServiceClient
import com.namely.protobuf.chief_of_state.writeside_handler.WriteSideHandlerServiceClient
import com.softwaremill.macwire.wire
import lagompb.{LagompbAggregate, LagompbApplication, LagompbCommandHandler, LagompbEventHandler}

/**
 * ChiefOfState application
 *
 * @param context
 */
abstract class ChiefOfStateApplication(context: LagomApplicationContext) extends LagompbApplication(context) {
  // $COVERAGE-OFF$

  // wiring up the grpc for the writeSide client
  lazy val writeSideHandlerServiceClient: WriteSideHandlerServiceClient = WriteSideHandlerServiceClient(
    GrpcClientSettings.fromConfig("chief_of_state.WriteSideHandlerService")(actorSystem)
  )

  // let us wire up the handler settings
  // this will break the application bootstrapping if the handler settings env variables are not set
  lazy val handlerSetting: ChiefOfStateHandlerSetting = ChiefOfStateHandlerSetting(config)

  //  Register a shutdown task to release resources of the client
  coordinatedShutdown
    .addTask(CoordinatedShutdown.PhaseServiceUnbind, "shutdown-writeSidehandler-service-client") { () =>
      writeSideHandlerServiceClient.close()
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

    // wiring up the grpc for the readSide client
    lazy val readSideHandlerServiceClient: ReadSideHandlerServiceClient = ReadSideHandlerServiceClient(
      GrpcClientSettings.fromConfig("chief_of_state.ReadSideHandlerService")(actorSystem)
    )

    //  Register a shutdown task to release resources of the client
    coordinatedShutdown
      .addTask(CoordinatedShutdown.PhaseServiceUnbind, "shutdown-readSidehandler-service-client") { () =>
        readSideHandlerServiceClient.close()
      }

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
