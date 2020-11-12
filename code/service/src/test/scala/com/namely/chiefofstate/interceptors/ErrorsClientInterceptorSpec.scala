package com.namely.chiefofstate.interceptors

import com.namely.chiefofstate.helper.{BaseSpec, GrpcHelpers}
import io.opentracing.contrib.grpc.TracingClientInterceptor
import io.opentracing.log.Fields
import io.opentracing.mock._
import com.namely.protobuf.chiefofstate.test.ping_service.PingServiceGrpc
import io.grpc.ManagedChannel
import scala.concurrent.Future
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.inprocess.InProcessChannelBuilder
import io.opentracing.util.GlobalTracer
import com.namely.chiefofstate.helper.PingServiceImpl
import io.grpc.Status
import com.namely.protobuf.chiefofstate.test.ping_service.Ping
import scala.util.Try
import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext.global

class ErrorsClientInterceptorSpec extends BaseSpec {
  import GrpcHelpers.Closeables

  val tracer: MockTracer = GrpcHelpers.mockTracer

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

  "interceptor" should {
    "report a server error" in {
      // Generate a unique in-process server name.
      val serverName: String = InProcessServerBuilder.generateName();

      val serviceImpl = new PingServiceImpl()
      val service = PingServiceGrpc.bindService(serviceImpl, global)

      // make the service return an error
      val err: Throwable = Status.ABORTED.withDescription("inner exception").asException()
      val handler = (request: Ping) => Future.failed(err)
      serviceImpl.setHandler(handler)

      // register a server that intercepts traces and reports errors
      closeables.register(
        InProcessServerBuilder
          .forName(serverName)
          .directExecutor()
          .addService(service)
          .build()
          .start()
      )

      val errorInterceptor = new ErrorsClientInterceptor(tracer)

      val channel: ManagedChannel = {
        closeables.register(
          InProcessChannelBuilder
            .forName(serverName)
            .directExecutor()
            .intercept(errorInterceptor)
            .build()
        )
      }

      // start a span and send a message that fails
      val span = tracer.buildSpan("outer").start()
      GlobalTracer.get().activateSpan(span)
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

      finishedSpans.length shouldBe 1

      // confirm error is in the logs
      val logs = finishedSpans.flatMap(_.logEntries().asScala.toSeq).toSeq
      logs.size shouldBe 1
      logs.head.fields().asScala(Fields.EVENT) shouldBe "error"
      logs.head.fields().asScala(Fields.MESSAGE) shouldBe err.getMessage
    }
  }
}
