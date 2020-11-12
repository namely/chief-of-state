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

class ErrorsServerInterceptorSpec extends BaseSpec {
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

  "server interceptor" should {
    "report inner errors" in {

      val tracer = new MockTracer(MockTracer.Propagator.TEXT_MAP)

      // Generate a unique in-process server name.
      val serverName: String = InProcessServerBuilder.generateName();

      // make a mock service that returns an error
      val serviceImpl: PingServiceGrpc.PingService = mock[PingServiceGrpc.PingService]
      val err: Throwable = Status.ABORTED.withDescription("inner exception").asException()
      (serviceImpl.send _)
        .expects(*)
        .returning(Future.failed(err))

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

      // start a span and send a message that fails
      val span = tracer.buildSpan("outer").ignoreActiveSpan().start()
      tracer.activateSpan(span)
      val stub = PingServiceGrpc.blockingStub(channel)
      val actual = Try(stub.send(Ping("foo")))
      span.finish()

      actual.isFailure shouldBe true

      // get the finished spans
      val finishedSpans: Seq[MockSpan] = tracer
        .finishedSpans()
        .asScala
        .toSeq
        .sortBy(_.context().spanId())

      finishedSpans.length shouldBe 2

      // confirm error is in the logs
      val logs = finishedSpans.flatMap(_.logEntries().asScala.toSeq).toSeq
      logs.size shouldBe 1
      logs.head.fields().asScala(Fields.EVENT) shouldBe "error"
      logs.head.fields().asScala(Fields.MESSAGE) shouldBe err.getMessage
    }
  }
}
