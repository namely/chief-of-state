/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import akka.NotUsed
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.Cluster
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.persistence.typed.PersistenceId
import akka.util.Timeout
import com.namely.chiefofstate.config.{CosConfig, ReadSideConfigReader}
import com.namely.chiefofstate.plugin.PluginManager
import com.namely.chiefofstate.telemetry._
import com.namely.protobuf.chiefofstate.v1.readside.ReadSideHandlerServiceGrpc.ReadSideHandlerServiceBlockingStub
import com.namely.protobuf.chiefofstate.v1.service.ChiefOfStateServiceGrpc.ChiefOfStateService
import com.namely.protobuf.chiefofstate.v1.writeside.WriteSideHandlerServiceGrpc.WriteSideHandlerServiceBlockingStub
import com.typesafe.config.Config
import io.grpc._
import io.grpc.netty.NettyServerBuilder
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.instrumentation.grpc.v1_5.client.TracingClientInterceptor
import io.opentelemetry.instrumentation.grpc.v1_5.server.TracingServerInterceptor
import org.slf4j.{Logger, LoggerFactory}

import java.net.InetSocketAddress
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.sys.ShutdownHookThread

/**
 * This helps setup the required engines needed to smoothly run the ChiefOfState sevice.
 * The following engines are started on boot.
 * <ul>
 *   <li> the akka cluster system
 *   <li> the akka cluster sharding engine
 *   <li> the akka cluster management
 *   <li> loads the various ChiefOfState plugins
 *   <li> run the required schemas and migration needed
 *   <li> the telemetry tools and the various gRPC interceptors
 *   <li> the gRPC service
 * </ul>
 */
object StartNodeBehaviour {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  def apply(config: Config): Behavior[NotUsed] = {
    Behaviors.setup { context =>
      implicit val ec: ExecutionContextExecutor = context.system.executionContext

      // get the  COS config
      val cosConfig: CosConfig = CosConfig(config)

      val cluster: Cluster = Cluster(context.system)
      context.log.info(s"starting node with roles: ${cluster.selfMember.roles}")

      // Start the akka cluster management tool
      AkkaManagement(context.system).start()
      ClusterBootstrap(context.system).start()

      // create data stores and run migrations if necessary
      if (cosConfig.createDataStores) {
        log.info("kick-starting the ChiefOfState journal, snapshot and offset stores creation")
        SchemasUtil
          .createIfNotExists(config)(context.system)
          .map(_ => {
            log.info("ChiefOfState journal, snapshot and offset stores created sucessfully...:)")
          })
      } else {
        log.info("No need to create stores")
      }

      // start the telemetry tools and register global tracer
      TelemetryTools(cosConfig).start()

      val tracer = GlobalOpenTelemetry.get().getTracer("com.namely.chiefofstate")

      // We only proceed when the data stores and various migrations are done successfully.
      log.info("Journal and snapshot store created successfully. About to start...")

      val channel: ManagedChannel =
        NettyHelper
          .builder(
            cosConfig.writeSideConfig.host,
            cosConfig.writeSideConfig.port,
            cosConfig.writeSideConfig.useTls
          )
          .build()

      val grpcClientInterceptors: Seq[ClientInterceptor] = Seq(
        TracingClientInterceptor.newInterceptor(tracer)
      )

      val writeHandler: WriteSideHandlerServiceBlockingStub = new WriteSideHandlerServiceBlockingStub(channel)
        .withInterceptors(grpcClientInterceptors: _*)

      val remoteCommandHandler: RemoteCommandHandler = RemoteCommandHandler(cosConfig.grpcConfig, writeHandler)
      val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(cosConfig.grpcConfig, writeHandler)

      // instance of eventsAndStatesProtoValidation
      val eventsAndStateProtoValidation: ProtosValidator = ProtosValidator(
        cosConfig.writeSideConfig
      )

      val sharding: ClusterSharding = ClusterSharding(context.system)

      sharding.init(
        Entity(typeKey = AggregateRoot.TypeKey) { entityContext =>
          AggregateRoot(
            PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
            Util.getShardIndex(entityContext.entityId, cosConfig.eventsConfig.numShards),
            cosConfig,
            remoteCommandHandler,
            remoteEventHandler,
            eventsAndStateProtoValidation
          )
        }
      )

      // read side settings
      startReadSide(context.system, cosConfig, grpcClientInterceptors)

      // start the service
      startService(sharding, config, cosConfig)

      Behaviors.empty
    }
  }

  private def startReadSide(system: ActorSystem[_],
                            cosConfig: CosConfig,
                            interceptors: Seq[ClientInterceptor]
  ): Unit = {
    if (cosConfig.enableReadSide && ReadSideConfigReader.getReadSideSettings.nonEmpty) {
      ReadSideConfigReader.getReadSideSettings.foreach(rsconfig => {
        val rpcClient: ReadSideHandlerServiceBlockingStub = new ReadSideHandlerServiceBlockingStub(
          NettyHelper
            .builder(rsconfig.host, rsconfig.port, rsconfig.useTls)
            .build
        ).withInterceptors(interceptors: _*)

        val remoteReadSideProcessor: RemoteReadSideProcessor = new RemoteReadSideProcessor(rpcClient)

        val readSideProcessor: ReadSideProcessor =
          new ReadSideProcessor(system, rsconfig.processorId, remoteReadSideProcessor, cosConfig)

        readSideProcessor.init()
      })
    }
  }

  /**
   * starts the main application
   */
  private def startService(clusterSharding: ClusterSharding,
                           config: Config,
                           cosConfig: CosConfig
  ): ShutdownHookThread = {
    implicit val askTimeout: Timeout = cosConfig.askTimeout

    // create the traced execution context for grpc
    val grpcEc: ExecutionContext = TracedExecutorService.get()

    val tracer = GlobalOpenTelemetry.get().getTracer("com.namely.chiefofstate")

    // create interceptor using the global tracer
    val tracingServerInterceptor: ServerInterceptor = TracingServerInterceptor
      .newInterceptor(tracer)

    // instantiate the grpc service, bind do the execution context
    val serviceImpl: GrpcServiceImpl =
      new GrpcServiceImpl(clusterSharding,
                          PluginManager.getPlugins(config),
                          cosConfig.writeSideConfig
      )

    // intercept the service
    val service: ServerServiceDefinition = ServerInterceptors.intercept(
      ChiefOfStateService.bindService(serviceImpl, grpcEc),
      tracingServerInterceptor,
      GrpcHeadersInterceptor
    )

    // attach service to netty server
    val server: Server = NettyServerBuilder
      .forAddress(new InetSocketAddress(cosConfig.grpcConfig.server.host, cosConfig.grpcConfig.server.port))
      .addService(service)
      .build()
      .start()

    log.info("ChiefOfState Service started, listening on " + cosConfig.grpcConfig.server.port)
    server.awaitTermination()
    sys.addShutdownHook {
      log.info("shutting down ChiefOfState service....")
      server.shutdown()
    }
  }
}
