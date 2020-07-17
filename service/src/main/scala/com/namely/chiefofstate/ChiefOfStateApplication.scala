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
import com.namely.protobuf.chief_of_state.cos_persistence.State
import com.namely.protobuf.chief_of_state.cos_readside_handler.ReadSideHandlerServiceClient
import com.namely.protobuf.chief_of_state.cos_writeside_handler.WriteSideHandlerServiceClient
import com.softwaremill.macwire.wire
import io.superflat.lagompb.{AggregateRoot, BaseApplication, CommandHandler, EventHandler}
import io.superflat.lagompb.encryption.{NoEncryption, ProtoEncryption}

/**
 * ChiefOfState application
 *
 * @param context
 */
abstract class ChiefOfStateApplication(context: LagomApplicationContext) extends BaseApplication(context) {
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

  // let us wire up for now the default encryption
  lazy val encryption: ProtoEncryption = NoEncryption

  // wire up the various event and command handler
  lazy val eventHandler: EventHandler[State] = wire[ChiefOfStateEventHandler]
  lazy val commandHandler: CommandHandler[State] = wire[ChiefOfStateCommandHandler]
  lazy val aggregate: AggregateRoot[State] = wire[ChiefOfStateAggregate]

  override def aggregateRoot: AggregateRoot[_] = aggregate

  override def server: LagomServer =
    serverFor[ChiefOfStateService](wire[ChiefOfStateServiceImpl])
      .additionalRouter(wire[ChiefOfStateGrpcServiceImpl])

  if (config.getBoolean("chief-of-state.read-model.enabled")) {

    // wiring up the grpc for the readSide client
    ChiefOfStateHelper.getReadSideConfigs
      .foreach({ case (processorId, settings) =>

        lazy val clientSettings: GrpcClientSettings = GrpcClientSettings.connectToServiceAt(
          settings("HOST"),
          settings("PORT").toInt
        )(actorSystem)

        // TODO: Add GRPCSettings

        lazy val readSideHandlerServiceClient: ReadSideHandlerServiceClient = ReadSideHandlerServiceClient(clientSettings)

        coordinatedShutdown.addTask(
          CoordinatedShutdown.PhaseServiceUnbind,
          s"shutdown-readSidehandler-service-client-${processorId}"
        ) { () =>
          readSideHandlerServiceClient.close()
        }

        lazy val chiefOfStateReadProcessor: ChiefOfStateReadProcessor = new ChiefOfStateReadProcessor(
          processorId,
          encryption,
          actorSystem,
          readSideHandlerServiceClient,
          handlerSetting
        )
        chiefOfStateReadProcessor.init()
      })
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
