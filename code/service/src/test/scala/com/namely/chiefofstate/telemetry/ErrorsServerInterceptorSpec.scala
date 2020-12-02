package com.namely.chiefofstate.common.telemetry

import com.namely.protobuf.reportbuilder.v1.helloworld.{GreeterGrpc, HelloRequest}
import com.namely.protobuf.reportbuilder.v1.helloworld.GreeterGrpc.Greeter
import com.namely.chiefofstate.common.TestSpec
import io.grpc.{ManagedChannel, Status}
import io.grpc.inprocess.{InProcessChannelBuilder, InProcessServerBuilder}
import io.opentracing.contrib.grpc.TracingServerInterceptor
import io.opentracing.log.Fields
import io.opentracing.mock.{MockSpan, MockTracer}

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.Try

class ErrorsServerInterceptorSpec extends TestSpec {

  "server interceptor" should {
    "report inner errors" in {

      val tracer = new MockTracer(MockTracer.Propagator.TEXT_MAP)

      // Generate a unique in-process server name.
      val serverName: String = InProcessServerBuilder.generateName();

      // make a mock service that returns an error
      val serviceImpl: Greeter = mock[Greeter]
      val err: Throwable = Status.ABORTED.withDescription("inner exception").asException()
      (serviceImpl.sayHello _)
        .expects(*)
        .returning(Future.failed(err))

      val service = GreeterGrpc.bindService(serviceImpl, global)

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

      val channel: ManagedChannel =
        closeables.registerChannel(
          InProcessChannelBuilder
            .forName(serverName)
            .directExecutor()
            .build()
        )

      // start a span and send a message that fails
      val span = tracer.buildSpan("outer").ignoreActiveSpan().start()
      tracer.activateSpan(span)
      val stub = GreeterGrpc.blockingStub(channel)
      val actual = Try(stub.sayHello(HelloRequest("foo")))
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
