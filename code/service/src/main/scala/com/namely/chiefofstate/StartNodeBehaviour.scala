/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import akka.{actor, NotUsed}
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.Cluster
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.persistence.typed.PersistenceId
import akka.util.Timeout
import com.namely.chiefofstate.config.{CosConfig, ReadSideConfigReader}
import com.namely.chiefofstate.migration.{CreateSchemas, DbQuery, DropSchemas, JdbcConfig}
import com.namely.chiefofstate.migration.legacy.LegacyMigrator
import com.namely.chiefofstate.plugin.PluginManager
import com.namely.chiefofstate.telemetry._
import com.namely.protobuf.chiefofstate.v1.readside.ReadSideHandlerServiceGrpc.ReadSideHandlerServiceBlockingStub
import com.namely.protobuf.chiefofstate.v1.service.ChiefOfStateServiceGrpc.ChiefOfStateService
import com.namely.protobuf.chiefofstate.v1.writeside.WriteSideHandlerServiceGrpc.WriteSideHandlerServiceBlockingStub
import com.typesafe.config.Config
import io.grpc._
import io.grpc.netty.NettyServerBuilder
import io.opentracing.contrib.grpc.{TracingClientInterceptor, TracingServerInterceptor}
import io.opentracing.util.GlobalTracer
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.{JdbcProfile, PostgresProfile}

import java.net.InetSocketAddress
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.concurrent.duration.Duration
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

  def apply(config: Config): Behavior[NotUsed] = Behaviors.setup { context =>
    implicit val ec: ExecutionContextExecutor = context.executionContext
    implicit val sys: ActorSystem[Nothing] = context.system

    // get the  COS config
    val cosConfig: CosConfig = CosConfig(config)

    val cluster: Cluster = Cluster(context.system)
    context.log.info(s"starting node with roles: ${cluster.selfMember.roles}")

    // Start the akka cluster management tool
    AkkaManagement(context.system).start()
    ClusterBootstrap(context.system).start()

    // in case of any exception this actor will be stopped and the whole
    // actor systemm will be shutdown because the supervisory strategy adopted is the
    // akka.actor.StoppingSupervisorStrategy. This is a bit more brutal but convenient to
    // our use case instead of the akka.actor.DefaultSupervisorStrategy which is a bit lenient
    // reference: https://doc.akka.io/docs/akka/2.5/general/supervision.html#user-the-guardian-actor
    prepareStorage(cosConfig)

    val channel: ManagedChannel =
      NettyHelper
        .builder(
          cosConfig.writeSideConfig.host,
          cosConfig.writeSideConfig.port,
          cosConfig.writeSideConfig.useTls
        )
        .build()

    val grpcClientInterceptors: Seq[ClientInterceptor] = Seq(
      new ErrorsClientInterceptor(GlobalTracer.get()),
      TracingClientInterceptor.newBuilder().withTracer(GlobalTracer.get()).build()
    )

    val writeHandler: WriteSideHandlerServiceBlockingStub = new WriteSideHandlerServiceBlockingStub(channel)
      .withInterceptors(grpcClientInterceptors: _*)

    val remoteCommandHandler: RemoteCommandHandler = RemoteCommandHandler(cosConfig.grpcConfig, writeHandler)
    val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(cosConfig.grpcConfig, writeHandler)

    // instance of eventsAndStatesProtoValidation
    val eventsAndStateProtoValidation: ProtosValidator = ProtosValidator(
      cosConfig.writeSideConfig
    )

    // start the telemetry tools and register global tracer
    TelemetryTools(config, cosConfig.enableJaeger, "chief-of-state").start()

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

  /**
   * setup the ChiefOfState data stores
   *
   * @param cosConfig the cos configuration
   * @param system the actor system
   * @param executionContext the execution context
   */
  private def prepareStorage(
    cosConfig: CosConfig
  )(implicit system: ActorSystem[_], executionContext: ExecutionContext): Unit = {
    system.log.info("kick-starting the preparation of ChiefOfState Stores...")

    implicit val classicSys: actor.ActorSystem = system.toClassic
    val config: Config = system.settings.config

    val journalJdbcConfig: DatabaseConfig[JdbcProfile] = JdbcConfig.journalConfig(config)
    val projectionJdbcConfig: DatabaseConfig[PostgresProfile] = JdbcConfig.projectionConfig(config)
    val createSchemas: CreateSchemas = CreateSchemas(journalJdbcConfig, projectionJdbcConfig)
    val dropSchemas: DropSchemas = migration.DropSchemas(journalJdbcConfig)
    val legacyMigrator: LegacyMigrator = LegacyMigrator(config, projectionJdbcConfig)
    val dbQuery: DbQuery = DbQuery(config, journalJdbcConfig)

    val legacyStoresExist: Boolean = Await.result(dbQuery.checkIfLegacyTablesExist(), Duration.Inf)
    if (legacyStoresExist) {
      system.log.info("ChiefOfState legacy stores exist...")
      val future: Future[Unit] = for {
        _ <- dropSchemas.journalStoresIfExist()
        _ <- createSchemas.journalStoresIfNotExists()
        _ <- legacyMigrator.run()
        _ <- dropSchemas.legacyJournalStoresIfExist()
        _ <- createSchemas.readSideOffsetStoreIfNotExist()
      } yield ()
      Await.result(future, Duration.Inf)
    } else {
      Await.result(
        for {
          _ <- createSchemas.journalStoresIfNotExists()
          _ <- createSchemas.readSideOffsetStoreIfNotExist()
        } yield (),
        Duration.Inf
      )
    }
    system.log.info("ChiefOfState Stores successfully prepared. :)")
  }

  /**
   * starts the read side
   *
   * @param system the actor system
   * @param cosConfig the cos config
   * @param interceptors the client gRPC interceptors
   */
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
    val grpcEc: ExecutionContext = TracedExecutionContext.get()

    // create interceptor using the global tracer
    val tracingServerInterceptor: TracingServerInterceptor = TracingServerInterceptor
      .newBuilder()
      .withTracer(GlobalTracer.get())
      .build()

    // instantiate the grpc service, bind do the execution context
    val serviceImpl: GrpcServiceImpl =
      new GrpcServiceImpl(clusterSharding,
                          PluginManager.getPlugins(config),
                          cosConfig.writeSideConfig,
                          GlobalTracer.get()
      )

    // intercept the service
    val service: ServerServiceDefinition = ServerInterceptors.intercept(
      ChiefOfStateService.bindService(serviceImpl, grpcEc),
      new ErrorsServerInterceptor(GlobalTracer.get()),
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
