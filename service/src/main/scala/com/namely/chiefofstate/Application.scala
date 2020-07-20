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
import com.namely.protobuf.chief_of_state.readside.ReadSideHandlerServiceClient
import com.namely.protobuf.chief_of_state.writeside.WriteSideHandlerServiceClient
import com.softwaremill.macwire.wire
import io.superflat.lagompb.{AggregateRoot, BaseApplication, CommandHandler, EventHandler}
import io.superflat.lagompb.encryption.{NoEncryption, ProtoEncryption}

/**
 * ChiefOfState application
 *
 * @param context
 */
abstract class Application(context: LagomApplicationContext) extends BaseApplication(context) {
  // $COVERAGE-OFF$

  // wiring up the grpc for the writeSide client
  lazy val writeSideHandlerServiceClient: WriteSideHandlerServiceClient = WriteSideHandlerServiceClient(
    GrpcClientSettings.fromConfig("chief_of_state.WriteSideHandlerService")(actorSystem)
  )

  // let us wire up the handler settings
  // this will break the application bootstrapping if the handler settings env variables are not set
  lazy val handlerSetting: HandlerSetting = HandlerSetting(config)

  //  Register a shutdown task to release resources of the client
  coordinatedShutdown
    .addTask(CoordinatedShutdown.PhaseServiceUnbind, "shutdown-writeSidehandler-service-client") { () =>
      writeSideHandlerServiceClient.close()
    }

  // let us wire up for now the default encryption
  lazy val encryption: ProtoEncryption = NoEncryption

  // wire up the various event and command handler
  lazy val eventHandler: EventHandler[State] = wire[AggregateEventHandler]
  lazy val commandHandler: CommandHandler[State] = wire[AggregateCommandHandler]
  lazy val aggregate: AggregateRoot[State] = wire[Aggregate]

  override def aggregateRoot: AggregateRoot[_] = aggregate

  override def server: LagomServer =
    serverFor[ChiefOfStateService](wire[RestServiceImpl])
      .additionalRouter(wire[GrpcServiceImpl])

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

    lazy val chiefOfStateReadProcessor: ReadSideHandler = wire[ReadSideHandler]
    chiefOfStateReadProcessor.init()
  }

  // $COVERAGE-ON$
}

/**
 * ChiefOfStateApplicationLoader boostraps the application at runtime
 */
class ApplicationLoader extends LagomApplicationLoader {

  // $COVERAGE-OFF$
  override def load(context: LagomApplicationContext): LagomApplication =
    new Application(context) with AkkaDiscoveryComponents

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new Application(context) with LagomDevModeComponents

  override def describeService: Option[Descriptor] = Some(readDescriptor[ChiefOfStateService])

  // $COVERAGE-ON$
}
