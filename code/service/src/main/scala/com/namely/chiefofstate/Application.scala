package com.namely.chiefofstate

import java.net.InetSocketAddress

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.persistence.typed.PersistenceId
import akka.util.Timeout
import com.namely.chiefofstate.config.{CosConfig, ReadSideConfigReader}
import com.namely.chiefofstate.plugin.PluginManager
import com.namely.chiefofstate.interceptors.GrpcHeadersInterceptor
import com.namely.protobuf.chiefofstate.v1.readside.ReadSideHandlerServiceGrpc.ReadSideHandlerServiceBlockingStub
import com.namely.protobuf.chiefofstate.v1.service.ChiefOfStateServiceGrpc.ChiefOfStateService
import com.namely.protobuf.chiefofstate.v1.writeside.WriteSideHandlerServiceGrpc.WriteSideHandlerServiceBlockingStub
import com.typesafe.config.{Config, ConfigFactory}
import io.grpc.{ManagedChannel, Server, ServerInterceptors}
import io.grpc.netty.{NettyChannelBuilder, NettyServerBuilder}
import org.slf4j.{Logger, LoggerFactory}
import scala.concurrent.ExecutionContext
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer
import io.opentracing.contrib.grpc.TracingServerInterceptor
import com.namely.chiefofstate.interceptors.ErrorsServerInterceptor
import io.jaegertracing.micrometer.MicrometerMetricsFactory
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.core.instrument.MeterRegistry
import io.opentracing.contrib.metrics.micrometer.MicrometerMetricsReporter
import io.opentracing.noop.NoopTracerFactory
import io.micrometer.core.instrument.Metrics
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.micrometer.prometheus.PrometheusConfig

class Application(clusterSharding: ClusterSharding, cosConfig: CosConfig, pluginManager: PluginManager) {
  self =>
  private[this] var server: Server = null
  final val log: Logger = LoggerFactory.getLogger(getClass)

  implicit private val askTimeout: Timeout = cosConfig.askTimeout

  /**
   * start the grpc server
   */
  private def start(): Unit = {
    // create a composite registry
    val compositeRegistry = new CompositeMeterRegistry()
    // set registry as global
    Metrics.addRegistry(compositeRegistry)
    // add a simple registry to it
    val simple: SimpleMeterRegistry = new SimpleMeterRegistry();
    compositeRegistry.add(simple)
    // create prometheus registry
    val prometheusRegistry: PrometheusMeterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    compositeRegistry.add(prometheusRegistry)

    val metricsReporter: MicrometerMetricsReporter = MicrometerMetricsReporter
      .newMetricsReporter()
      .withRegistry(compositeRegistry)
      .withName("Micrometer") // TODO: name?
      .build()

    // TODO: move this into a tracing helper
    val tracer: Tracer = {
      // start with a no-op tracer
      var tmpTracer: Tracer = NoopTracerFactory.create()

      // conditionally set up Jaeger
      if (cosConfig.enableJaeger) {
        // create & register jaeger tracer
        val jaegerTracer: Tracer = io.jaegertracing.Configuration
          .fromEnv()
          // add a metrics factory for jaeger internal metrics?
          // TODO: do we need this?
          .withMetricsFactory(new MicrometerMetricsFactory())
          .getTracer()

        tmpTracer = jaegerTracer
      }

      // create a decorated tracer with our tracer and the micrometer reporter
      tmpTracer = io.opentracing.contrib.metrics.Metrics.decorate(tmpTracer, metricsReporter)

      GlobalTracer.registerIfAbsent(tmpTracer)

      tmpTracer
    }

    // create interceptor using the global tracer
    val tracingServerInterceptor = TracingServerInterceptor
      .newBuilder()
      .withTracer(tracer)
      .build()

    server = NettyServerBuilder
      .forAddress(new InetSocketAddress(cosConfig.grpcConfig.server.host, cosConfig.grpcConfig.server.port))
      .addService(
        ServerInterceptors.intercept(
          ChiefOfStateService.bindService(
            new GrpcServiceImpl(clusterSharding, pluginManager, cosConfig.writeSideConfig, tracer),
            ExecutionContext.global
          ),
          new ErrorsServerInterceptor(GlobalTracer.get()),
          tracingServerInterceptor,
          GrpcHeadersInterceptor
        )
      )
      .build()
      .start()

    val prometheusServer: PrometheusServer = PrometheusServer(prometheusRegistry, 8888)
    prometheusServer.start()

    log.info("gRPC Server started, listening on " + cosConfig.grpcConfig.server.port)

    sys.addShutdownHook {
      prometheusServer.stop()
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
  val config: Config = ConfigFactory.load().resolve()

  // Initialize Plugin Manager
  val pluginManager: PluginManager = PluginManager.getPlugins(config)

  // load the main application config
  val cosConfig: CosConfig = CosConfig(config)

  // instance of eventsAndStatesProtoValidation
  val eventsAndStateProtoValidation: EventsAndStateProtosValidation = EventsAndStateProtosValidation(
    cosConfig.writeSideConfig
  )

  // boot the actor system
  val actorSystem: ActorSystem[Nothing] =
    ActorSystem[Nothing](Behaviors.empty, "ChiefOfStateSystem", config)

  // instance of the clusterSharding
  val sharding: ClusterSharding = ClusterSharding(actorSystem)

  val channel: ManagedChannel =
    NettyChannelBuilder
      .forAddress(cosConfig.writeSideConfig.host, cosConfig.writeSideConfig.port)
      .usePlaintext()
      .build()

  val writeHandler: WriteSideHandlerServiceBlockingStub = new WriteSideHandlerServiceBlockingStub(channel)
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
      val channel: ManagedChannel =
        NettyChannelBuilder
          .forAddress(rsconfig.host.get, rsconfig.port.get)
          .usePlaintext()
          .build()

      val rpcClient: ReadSideHandlerServiceBlockingStub = new ReadSideHandlerServiceBlockingStub(channel)
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
