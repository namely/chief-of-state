package com.namely.chiefofstate

import akka.actor.CoordinatedShutdown
import akka.dispatch.MessageDispatcher
import akka.event.LoggingAdapter
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
import com.namely.chiefofstate.config.{EncryptionSetting, HandlerSetting, ReadSideSetting, SendCommandSettings}
import com.namely.chiefofstate.grpc.client.{ReadSideHandlerServiceClient, WriteSideHandlerServiceClient}
import com.namely.chiefofstate.readside.ReadProcessor
import io.superflat.lagompb.{BaseApplication, CommandHandler, EventHandler}
import io.superflat.lagompb.encryption.ProtoEncryption
import kamon.Kamon
import org.slf4j.{Logger, LoggerFactory}

/**
 * ChiefOfState application
 *
 * @param context the application context
 */
abstract class Application(context: LagomApplicationContext) extends BaseApplication(context) {

  lazy val applog: Logger = LoggerFactory.getLogger(getClass)

  // start kamon
  Kamon.init()

  // reflect encryption from config
  override def protoEncryption: Option[ProtoEncryption] = EncryptionSetting(config).encryption

  // let us wire up the handler settings
  // this will break the application bootstrapping if the handler settings env variables are not set
  lazy val handlerSetting: HandlerSetting = HandlerSetting(config)

  lazy val loggingAdapter: LoggingAdapter = akka.event.Logging(actorSystem.classicSystem, this.getClass)

  lazy val aggregateRoot = {
    // let us wire up the writeSide executor context
    val writeSideHandlerServiceClient: WriteSideHandlerServiceClient = {
      val writeSideExecutionContext: MessageDispatcher =
        actorSystem.dispatchers.lookup(handlerSetting.writeSideDispatcher)

      val writeClientSettings: GrpcClientSettings =
        GrpcClientSettings.fromConfig("chief_of_state.v1.WriteSideHandlerService")(actorSystem)

      WriteSideHandlerServiceClient(writeClientSettings)(writeSideExecutionContext, loggingAdapter)
    }

    //  Register a shutdown task to release resources of the client
    coordinatedShutdown
      .addTask(CoordinatedShutdown.PhaseServiceUnbind, "shutdown-writeSidehandler-service-client") { () =>
        writeSideHandlerServiceClient.close()
      }

    // wire up the various event and command handler
    val eventHandler: EventHandler = new AggregateEventHandler(writeSideHandlerServiceClient, handlerSetting)
    val commandHandler: CommandHandler = new AggregateCommandHandler(writeSideHandlerServiceClient, handlerSetting)

    new Aggregate(
      actorSystem,
      config,
      commandHandler,
      eventHandler,
      encryptionAdapter
    )
  }

  lazy val server: LagomServer = {
    // get the SendCommandSettings for the GrpcServiceImpl
    val sendCommandSettings: SendCommandSettings = SendCommandSettings(config)

    val restService: RestServiceImpl =
      new RestServiceImpl(clusterSharding, persistentEntityRegistry, aggregateRoot)

    val grpcService: GrpcServiceImpl =
      new GrpcServiceImpl(actorSystem, clusterSharding, aggregateRoot, sendCommandSettings)

    serverFor[ChiefOfStateService](restService)
      .additionalRouter(grpcService)
  }

  if (config.getBoolean("chief-of-state.read-model.enabled")) {
    val readSideExecutionContext = actorSystem.dispatchers.lookup(handlerSetting.readSideDispatcher)

    // wiring up the grpc for the readSide client
    ReadSideSetting.getReadSideSettings.foreach { config =>
      val readSideHandlerServiceClient: ReadSideHandlerServiceClient =
        ReadSideHandlerServiceClient(config.getGrpcClientSettings(actorSystem))(readSideExecutionContext,
                                                                                loggingAdapter
        )

      coordinatedShutdown.addTask(
        CoordinatedShutdown.PhaseServiceUnbind,
        s"shutdown-readSidehandler-service-client-${config.processorId}"
      ) { () =>
        readSideHandlerServiceClient.close()
      }

      // explicit initialization so that we can pass the desired execution context
      lazy val readSideProcessor: ReadProcessor =
        new ReadProcessor(config, encryptionAdapter, actorSystem, readSideHandlerServiceClient)(
          readSideExecutionContext
        )
      readSideProcessor.start()
    }
  }

  this.startAggregateRootCluster()
}

/**
 * ApplicationLoader boostraps the application at runtime
 */
class ApplicationLoader extends LagomApplicationLoader {
  override def load(context: LagomApplicationContext): LagomApplication =
    new Application(context) with AkkaDiscoveryComponents

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new Application(context) with LagomDevModeComponents

  override def describeService: Option[Descriptor] = Some(readDescriptor[ChiefOfStateService])
}
