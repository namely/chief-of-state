package com.namely.chiefofstate.interceptors

import com.namely.chiefofstate.helper.{BaseSpec, GrpcHelpers}
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.ManagedChannel;
import io.grpc.internal.AbstractServerImplBuilder
import scala.collection.mutable
import com.namely.protobuf.chiefofstate.test.ping_service._
import scala.concurrent.ExecutionContext.global
import io.grpc.stub.MetadataUtils
import io.grpc.Metadata
import io.opentracing.Tracer
import io.opentracing.Scope
import scala.jdk.CollectionConverters._
import io.opentracing.mock.MockSpan
import io.opentracing.mock.MockTracer
import io.opentracing.util.GlobalTracer
import io.opentracing.contrib.grpc.TracingServerInterceptor
import io.opentracing.Tracer.SpanBuilder
import scala.concurrent.Future
import scala.util.Try
import io.opentracing.log.Fields
import io.grpc.StatusException
import io.grpc.Status
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.core.instrument.Metrics
import io.opentracing.contrib.metrics.micrometer.MicrometerMetricsReporter
import io.opentracing.contrib.metrics.MetricsReporter
import io.opentracing.contrib.grpc.ActiveSpanSource
import io.opentracing.tag.Tags
import io.opentracing.tag.Tag
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.micrometer.prometheus.PrometheusConfig

class TestMetricsSpec extends BaseSpec {
  import GrpcHelpers.Closeables

  // define set of resources to close after each test
  val closeables: Closeables = new Closeables()

  override protected def beforeEach(): Unit = {
    super.beforeEach()
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    closeables.closeAll()
  }

  "metrics reporter" should {
    "do stuff" in {

      // set up micrometer with the global registry
      val simple: SimpleMeterRegistry = new SimpleMeterRegistry();
      // set up prometheus registry
      val prometheusRegistry: PrometheusMeterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

      val meterRegistry: MeterRegistry = {
        // create a composite registry
        val compositeRegistry = new CompositeMeterRegistry()
        // add a simple registry to it
        compositeRegistry.add(simple)
        // add prometheus registry
        compositeRegistry.add(prometheusRegistry)
        // set registry as global
        Metrics.addRegistry(compositeRegistry)
        // return it
        compositeRegistry
      }

      val metricsReporter: MetricsReporter = MicrometerMetricsReporter
        .newMetricsReporter()
        .withRegistry(meterRegistry)
        .withName("Micrometer") // TODO: name?
        .build()

      val mockTracer: MockTracer = new MockTracer(MockTracer.Propagator.TEXT_MAP)
      val tracer: Tracer = io.opentracing.contrib.metrics.Metrics.decorate(mockTracer, metricsReporter)
      GlobalTracer.registerIfAbsent(tracer)

      // Generate a unique in-process server name.
      val serverName: String = InProcessServerBuilder.generateName();

      // make a mock service that returns success
      val serviceImpl: PingServiceGrpc.PingService = mock[PingServiceGrpc.PingService]

      (serviceImpl.send _)
        .expects(*)
        .onCall((request: Ping) => {
          val span = GlobalTracer.get
            .buildSpan("inner")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
            .start()

          val innerSpan = GlobalTracer
            .get()
            .buildSpan("inner-inner")
            .asChildOf(span)
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
            .start()

          innerSpan.log("yayayayay")

          innerSpan.finish()

          span.log("yay")
          span.finish()
          Future.successful(Pong("pong"))
        })

      val service = PingServiceGrpc.bindService(serviceImpl, global)

      // create the error interceptor
      val interceptor = new ErrorsServerInterceptor(tracer)
      // create a tracing interceptor
      val tracingServerInterceptor = TracingServerInterceptor
        .newBuilder()
        .withTracer(tracer)
        .build()

      // register a server that intercepts traces and reports errors
      closeables.register(
        InProcessServerBuilder
          .forName(serverName)
          .directExecutor()
          .addService(service)
          .intercept(interceptor)
          .intercept(tracingServerInterceptor)
          .build()
          .start()
      )

      val channel: ManagedChannel = {
        closeables.register(
          InProcessChannelBuilder
            .forName(serverName)
            .directExecutor()
            .build()
        )
      }

      // start a span and send a message
      val span = tracer
        .buildSpan("outer")
        .ignoreActiveSpan()
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
        .start()
      mockTracer.activateSpan(span)
      val stub = PingServiceGrpc.blockingStub(channel)
      val actual = Try(stub.send(Ping("foo")))
      span.finish()
      actual.isSuccess shouldBe (true)

      // get the finished spans
      val finishedSpans: Seq[MockSpan] = mockTracer
        .finishedSpans()
        .asScala
        .toSeq
        .sortBy(_.context().spanId())

      // print all the spans
      println("SPANS **********")
      finishedSpans.foreach(println)

      // print all the meters
      println("METERS **********")
      simple
        .getMeters()
        .asScala
        .zipWithIndex
        .foreach({
          case (meter, ix) => {
            println(s"\n... meter $ix")
            println(meter.getId())
            println(meter.measure())
          }
        })

      println("prometheusRegistry *********")
      println(prometheusRegistry.scrape())
    }
  }
}
