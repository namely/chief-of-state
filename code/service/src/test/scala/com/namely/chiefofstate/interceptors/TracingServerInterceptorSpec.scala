package com.namely.chiefofstate.interceptors

import com.namely.chiefofstate.helper.BaseSpec
import com.google.protobuf.wrappers.StringValue
import io.grpc._
import io.grpc.ServerCall
import com.namely.protobuf.chiefofstate.v1.writeside._
import com.namely.chiefofstate.GrpcServiceImpl
import io.grpc.testing.GrpcCleanupRule
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
import kamon.instrumentation.futures.scala.ScalaFutureInstrumentation
import kamon.trace.Span

class TracingServerInterceptorSpec extends BaseSpec {

  import GrpcHelpers._

  // define set of resources to close after each test
  val resources: Closeables = new Closeables()

  override protected def afterEach(): Unit = {
    super.afterEach()
    resources.closeAll()
  }

  "interceptor" should {
    "propagate a trace through the server" in {
      // Generate a unique in-process server name.
      val serverName: String = InProcessServerBuilder.generateName();
      val serviceImpl = new PingServiceImpl()
      val service = PingServiceGrpc.bindService(serviceImpl, global)

      // declare a variable and interceptor to capture the headers
      // and the current span of that process
      var responseHeaders: Option[Metadata] = None
      var requestSpan: Option[Span] = None

      def intercept(p: Ping): Unit = {
        responseHeaders = Option(GrpcHeadersInterceptor.REQUEST_META.get())
        requestSpan = Option(kamon.Kamon.currentSpan())
      }

      serviceImpl.registerInterceptor(intercept)

      resources.register(
        InProcessServerBuilder
          .forName(serverName)
          .directExecutor()
          .addService(service)
          .intercept(TracingServerInterceptor)
          .intercept(GrpcHeadersInterceptor)
          .build()
          .start()
      )

      val channel: ManagedChannel = {
        resources.register(
          InProcessChannelBuilder
            .forName(serverName)
            .directExecutor()
            .intercept(TracingClientInterceptor)
            .build()
        )
      }

      val stub = PingServiceGrpc.blockingStub(channel)

      val span = kamon.Kamon.clientSpanBuilder("some test", this.getClass().getName).start()

      // send a command in a kamon span
      kamon.Kamon.runWithSpan(span, true) {
        stub.send(Ping("hi"))
      }

      // assert the intercepted trace ID matches the outer trace
      requestSpan.map(_.trace.id) shouldBe (Some(span.trace.id))
      // assert the intercepted headers include the b3 headers
      responseHeaders.isDefined shouldBe (true)
      GrpcHelpers.getStringHeader(responseHeaders.get, "x-b3-traceid") shouldBe (span.trace.id.string)
    }
  }

  ".getChildSpanBuilder" should {
    "yield a span builder for that process" in {
      val actual = TracingServerInterceptor.getChildSpanBuilder("x")
      actual.operationName() shouldBe ("x")
    }
  }
}
