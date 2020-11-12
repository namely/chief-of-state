package com.namely.chiefofstate.interceptors

import com.namely.chiefofstate.helper.{BaseSpec, GrpcHelpers}
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.ManagedChannel;
import io.grpc.internal.AbstractServerImplBuilder
import scala.collection.mutable
import com.namely.chiefofstate.helper.PingServiceImpl
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

  val tracer: MockTracer = GrpcHelpers.mockTracer
  GlobalTracer.registerIfAbsent(tracer)

  // define set of resources to close after each test
  val closeables: Closeables = new Closeables()

  override protected def beforeEach(): Unit = {
    GrpcHelpers.mockTracer.reset()
    super.beforeEach()
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    closeables.closeAll()
  }

  "server interceptor" should {
    "report inner errors" in {
      // Generate a unique in-process server name.
      val serverName: String = InProcessServerBuilder.generateName();
      val serviceImpl = new PingServiceImpl()
      val service = PingServiceGrpc.bindService(serviceImpl, global)
      val interceptor = new ErrorsServerInterceptor(tracer)

      // make the service return an error
      val err: Throwable = Status.ABORTED.withDescription("inner exception").asException()
      val handler = (request: Ping) => Future.failed(err)
      serviceImpl.setHandler(handler)

      // create interceptor using the global tracer
      val tracingServerInterceptor = TracingServerInterceptor
        .newBuilder()
        .withTracer(GlobalTracer.get())
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
      val span = tracer.buildSpan("outer").start()
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
