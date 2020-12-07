/*
 * MIT License
 *
 * Copyright (c) 2020 Namely
 */

package com.namely.chiefofstate.telemetry

import com.namely.protobuf.chiefofstate.test.helloworld.{GreeterGrpc, HelloReply, HelloRequest}
import com.namely.protobuf.chiefofstate.test.helloworld.GreeterGrpc.Greeter
import com.namely.chiefofstate.helper.BaseSpec
import io.grpc.{ManagedChannel, ServerServiceDefinition, Status}
import io.grpc.inprocess.{InProcessChannelBuilder, InProcessServerBuilder}
import io.opentracing.log.Fields
import io.opentracing.mock._

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.Try

class ErrorsClientInterceptorSpec extends BaseSpec {

  "interceptor" should {
    "report a server error" in {
      val tracer = new MockTracer(MockTracer.Propagator.TEXT_MAP)

      // Generate a unique in-process server name.
      val serverName: String = InProcessServerBuilder.generateName();

      // mock the service return an error
      val serviceImpl: Greeter = mock[Greeter]
      val err: Throwable = Status.ABORTED.withDescription("inner exception").asException()

      (serviceImpl.sayHello _)
        .expects(*)
        .returning(Future.failed(err))

      val service: ServerServiceDefinition = Greeter.bindService(serviceImpl, global)

      // register a server that intercepts traces and reports errors
      closeables.register(
        InProcessServerBuilder
          .forName(serverName)
          .directExecutor()
          .addService(service)
          .build()
          .start()
      )

      val errorInterceptor: ErrorsClientInterceptor = new ErrorsClientInterceptor(tracer)

      val channel: ManagedChannel =
        closeables.register(
          InProcessChannelBuilder
            .forName(serverName)
            .directExecutor()
            .intercept(errorInterceptor)
            .build()
        )

      // start a span and send a message that fails
      val span: MockSpan = tracer.buildSpan("outer").ignoreActiveSpan.start()
      tracer.activateSpan(span)
      val stub: GreeterGrpc.GreeterBlockingStub = GreeterGrpc.blockingStub(channel)
      val actual: Try[HelloReply] = Try(stub.sayHello(HelloRequest("foo")))
      span.finish()

      actual.isFailure shouldBe true

      // get the finished spans
      val finishedSpans: Seq[MockSpan] = tracer
        .finishedSpans()
        .asScala
        .toSeq
        .sortBy(_.context().spanId())

      finishedSpans.length shouldBe 1

      // confirm error is in the logs
      val logs: Seq[MockSpan.LogEntry] = finishedSpans.flatMap(_.logEntries().asScala.toSeq)
      logs.size shouldBe 1
      logs.head.fields().asScala(Fields.EVENT) shouldBe "error"
      logs.head.fields().asScala(Fields.MESSAGE) shouldBe err.getMessage
    }
  }
}
