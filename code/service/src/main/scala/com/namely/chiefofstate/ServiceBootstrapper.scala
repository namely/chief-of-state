/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import akka.actor.typed.{ ActorSystem, Behavior }
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity }
import akka.persistence.typed.PersistenceId
import akka.util.Timeout
import com.namely.chiefofstate.config.CosConfig
import com.namely.chiefofstate.plugin.PluginManager
import com.namely.chiefofstate.readside.ReadSideManager
import com.namely.protobuf.chiefofstate.v1.internal.MigrationDone
import com.namely.protobuf.chiefofstate.v1.service.ChiefOfStateServiceGrpc.ChiefOfStateService
import com.namely.protobuf.chiefofstate.v1.writeside.WriteSideHandlerServiceGrpc.WriteSideHandlerServiceBlockingStub
import com.typesafe.config.Config
import io.grpc._
import io.grpc.netty.NettyServerBuilder
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.instrumentation.grpc.v1_5.GrpcTracing
import io.superflat.otel.tools.{
  GrpcHeadersInterceptor,
  StatusClientInterceptor,
  StatusServerInterceptor,
  TelemetryTools,
  TracedExecutorService
}
import org.slf4j.{ Logger, LoggerFactory }

import java.net.InetSocketAddress
import scala.concurrent.ExecutionContext
import scala.sys.ShutdownHookThread

/**
 * This helps setup the required engines needed to smoothly run the ChiefOfState sevice.
 * The following engines are started on boot.
 * <ul>
 *   <li> the akka cluster sharding engine
 *   <li> loads the various ChiefOfState plugins
 *   <li> the telemetry tools and the various gRPC interceptors
 *   <li> the gRPC service
 * </ul>
 */
object ServiceBootstrapper {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  def apply(config: Config): Behavior[scalapb.GeneratedMessage] = Behaviors.setup[scalapb.GeneratedMessage] { context =>
    // get the  COS config
    val cosConfig: CosConfig = CosConfig(config)

    Behaviors.receiveMessage[scalapb.GeneratedMessage] {
      case _: MigrationDone =>
        // start the telemetry tools and register global tracer
        TelemetryTools(cosConfig.telemetryConfig).start()

        // We only proceed when the data stores and various migrations are done successfully.
        log.info("Journal and snapshot store created successfully. About to start...")

        val channel: ManagedChannel =
          NettyHelper
            .builder(cosConfig.writeSideConfig.host, cosConfig.writeSideConfig.port, cosConfig.writeSideConfig.useTls)
            .build()

        val grpcClientInterceptors: Seq[ClientInterceptor] =
          Seq(GrpcTracing.create(GlobalOpenTelemetry.get()).newClientInterceptor(), new StatusClientInterceptor())

        val writeHandler: WriteSideHandlerServiceBlockingStub =
          new WriteSideHandlerServiceBlockingStub(channel).withInterceptors(grpcClientInterceptors: _*)

        val remoteCommandHandler: RemoteCommandHandler = RemoteCommandHandler(cosConfig.grpcConfig, writeHandler)
        val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(cosConfig.grpcConfig, writeHandler)

        // instance of eventsAndStatesProtoValidation
        val eventsAndStateProtoValidation: ProtosValidator = ProtosValidator(cosConfig.writeSideConfig)

        // initialize the sharding extension
        val sharding: ClusterSharding = ClusterSharding(context.system)

        // initialize the shard region
        sharding.init(Entity(typeKey = AggregateRoot.TypeKey) { entityContext =>
          AggregateRoot(
            PersistenceId.ofUniqueId(entityContext.entityId),
            Util.getShardIndex(entityContext.entityId, cosConfig.eventsConfig.numShards),
            cosConfig,
            remoteCommandHandler,
            remoteEventHandler,
            eventsAndStateProtoValidation)
        })

        // read side settings
        startReadSide(context.system, cosConfig, grpcClientInterceptors)

        // start the service
        startService(sharding, config, cosConfig)

        Behaviors.same

      case unhandled =>
        log.warn(s"unhandled message ${unhandled.companion.scalaDescriptor.fullName}")

        Behaviors.stopped
    }
  }

  /**
   * starts the main application
   *
   * @param clusterSharding the akka cluster sharding
   * @param config the application configuration
   * @param cosConfig the cos specific configuration
   */
  private def startService(
      clusterSharding: ClusterSharding,
      config: Config,
      cosConfig: CosConfig): ShutdownHookThread = {
    implicit val askTimeout: Timeout = cosConfig.askTimeout

    // create the traced execution context for grpc
    val grpcEc: ExecutionContext = TracedExecutorService.get()

    // create interceptor using the global tracer
    val tracingServerInterceptor: ServerInterceptor =
      GrpcTracing.create(GlobalOpenTelemetry.get()).newServerInterceptor()

    // instantiate the grpc service, bind do the execution context
    val serviceImpl: GrpcServiceImpl =
      new GrpcServiceImpl(clusterSharding, PluginManager.getPlugins(config), cosConfig.writeSideConfig)

    // intercept the service
    val service: ServerServiceDefinition = ServerInterceptors.intercept(
      ChiefOfStateService.bindService(serviceImpl, grpcEc),
      tracingServerInterceptor,
      new StatusServerInterceptor(),
      GrpcHeadersInterceptor)

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

  /**
   * Start all the read side processors (akka projections)
   *
   * @param system actor system
   * @param cosConfig the chief of state config
   * @param interceptors gRPC client interceptors for remote calls
   */
  private def startReadSide(
      system: ActorSystem[_],
      cosConfig: CosConfig,
      interceptors: Seq[ClientInterceptor]): Unit = {
    // if read side is enabled
    if (cosConfig.enableReadSide) {
      // instantiate a read side manager
      val readSideManager: ReadSideManager =
        ReadSideManager(system = system, interceptors = interceptors, numShards = cosConfig.eventsConfig.numShards)
      // initialize all configured read sides
      readSideManager.init()
    }
  }
}
