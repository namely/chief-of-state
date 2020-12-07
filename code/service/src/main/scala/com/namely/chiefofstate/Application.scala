/*
 * MIT License
 *
 * Copyright (c) 2020 Namely
 */

package com.namely.chiefofstate

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.persistence.typed.PersistenceId
import akka.util.Timeout
import com.namely.chiefofstate.config.{BootConfig, CosConfig, ReadSideConfigReader}
import com.namely.chiefofstate.plugin.PluginManager
import com.namely.chiefofstate.telemetry._
import com.namely.protobuf.chiefofstate.v1.readside.ReadSideHandlerServiceGrpc.ReadSideHandlerServiceBlockingStub
import com.namely.protobuf.chiefofstate.v1.service.ChiefOfStateServiceGrpc.ChiefOfStateService
import com.namely.protobuf.chiefofstate.v1.writeside.WriteSideHandlerServiceGrpc.WriteSideHandlerServiceBlockingStub
import com.typesafe.config.Config
import io.grpc.netty.{NettyChannelBuilder, NettyServerBuilder}
import io.grpc.{ClientInterceptor, ManagedChannel, Server, ServerInterceptors}
import io.opentracing.contrib.grpc.{TracingClientInterceptor, TracingServerInterceptor}
import io.opentracing.util.GlobalTracer
import org.slf4j.{Logger, LoggerFactory}

import java.net.InetSocketAddress
import scala.concurrent.ExecutionContext

class Application(clusterSharding: ClusterSharding, cosConfig: CosConfig, pluginManager: PluginManager) {
  self =>
  private[this] var server: Server = null
  final val log: Logger = LoggerFactory.getLogger(getClass)

  implicit private val askTimeout: Timeout = cosConfig.askTimeout

  /**
   * start the grpc server
   */
  private def start(): Unit = {

    // create the traced execution context for grpc
    val grpcEc: ExecutionContext = TracedExecutionContext.get

    // create interceptor using the global tracer
    val tracingServerInterceptor = TracingServerInterceptor
      .newBuilder()
      .withTracer(GlobalTracer.get())
      .build()

    // instantiate the grpc service, bind do the execution context
    val serviceImpl = new GrpcServiceImpl(clusterSharding, pluginManager, cosConfig.writeSideConfig, GlobalTracer.get())
    var service = ChiefOfStateService.bindService(serviceImpl, grpcEc)

    // intercept the service
    service = ServerInterceptors.intercept(
      service,
      new ErrorsServerInterceptor(GlobalTracer.get()),
      tracingServerInterceptor,
      GrpcHeadersInterceptor
    )

    // attach service to netty server
    server = NettyServerBuilder
      .forAddress(new InetSocketAddress(cosConfig.grpcConfig.server.host, cosConfig.grpcConfig.server.port))
      .addService(service)
      .build()
      .start()

    log.info("gRPC Server started, listening on " + cosConfig.grpcConfig.server.port)

    sys.addShutdownHook {
      self.stop()
    }
  }

  /**
   * stops the grp server
   */
  private def stop(): Unit = {
    if (server != null) {
      log.info("*** shutting down gRPC server since JVM is shutting down")
      server.shutdown()
    }
  }

  private def blockUntilShutdown(): Unit = {
    if (server != null) {
      server.awaitTermination()
    }
  }
}

object Application extends App {
  // Application config

  val config: Config = BootConfig.get()

  // Initialize Plugin Manager
  val pluginManager: PluginManager = PluginManager.getPlugins(config)

  // load the main application config
  val cosConfig: CosConfig = CosConfig(config)

  // instance of eventsAndStatesProtoValidation
  val eventsAndStateProtoValidation: ProtosValidator = ProtosValidator(
    cosConfig.writeSideConfig
  )

  // start the telemetry tools and register global tracer
  TelemetryTools(config, cosConfig.enableJaeger, "chief-of-state").start()

  // boot the actor system
  val actorSystem: ActorSystem[Nothing] =
    ActorSystem[Nothing](StartupBehaviour(config, cosConfig.createDataStores), "ChiefOfStateSystem", config)

  // instance of the clusterSharding
  val sharding: ClusterSharding = ClusterSharding(actorSystem)

  val channel: ManagedChannel =
    NettyHelper
      .builder(
        cosConfig.writeSideConfig.host,
        cosConfig.writeSideConfig.port,
        cosConfig.writeSideConfig.useTls
      )
      .build()

  NettyChannelBuilder
    .forAddress(cosConfig.writeSideConfig.host, cosConfig.writeSideConfig.port)
    .useTransportSecurity()
    // .usePlaintext()
    .build()

  val grpcClientInterceptors: Seq[ClientInterceptor] = Seq(
    new ErrorsClientInterceptor(GlobalTracer.get()),
    TracingClientInterceptor.newBuilder().withTracer(GlobalTracer.get()).build()
  )

  val writeHandler: WriteSideHandlerServiceBlockingStub = new WriteSideHandlerServiceBlockingStub(channel)
    .withInterceptors(grpcClientInterceptors: _*)

  val remoteCommandHandler: RemoteCommandHandler = RemoteCommandHandler(cosConfig.grpcConfig, writeHandler)
  val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(cosConfig.grpcConfig, writeHandler)

  // registration at startup
  sharding.init(Entity(typeKey = AggregateRoot.TypeKey) { entityContext =>

    val shardIndex: Int = Util.getShardIndex(entityContext.entityId, cosConfig.eventsConfig.numShards)

    AggregateRoot(
      PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
      shardIndex,
      cosConfig,
      remoteCommandHandler,
      remoteEventHandler,
      eventsAndStateProtoValidation
    )
  })

  // Akka Management hosts the HTTP routes used by bootstrap
  AkkaManagement(actorSystem).start()

  // Starting the bootstrap process needs to be done explicitly
  ClusterBootstrap(actorSystem).start()

  // read side settings
  if (cosConfig.enableReadSide && ReadSideConfigReader.getReadSideSettings.nonEmpty) {
    ReadSideConfigReader.getReadSideSettings.foreach(rsconfig => {

      val channel: ManagedChannel = NettyHelper
        .builder(rsconfig.host, rsconfig.port, rsconfig.useTls)
        .build

      var rpcClient: ReadSideHandlerServiceBlockingStub = new ReadSideHandlerServiceBlockingStub(channel)
      rpcClient = rpcClient.withInterceptors(grpcClientInterceptors: _*)

      val remoteReadSideProcessor: RemoteReadSideProcessor = new RemoteReadSideProcessor(rpcClient)

      val readSideProcessor: ReadSideProcessor =
        new ReadSideProcessor(actorSystem, rsconfig.processorId, remoteReadSideProcessor, cosConfig)

      readSideProcessor.init()
    })
  }

  // start the gRPC server
  val server: Application = new Application(sharding, cosConfig, pluginManager)
  server.start()
  server.blockUntilShutdown()
}
