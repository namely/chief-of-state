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

class GrpcHeadersInterceptorSpec extends BaseSpec {
  import GrpcHelpers._

  // define set of resources to close after each test
  val closeables: Closeables = new Closeables()

  override protected def afterEach(): Unit = {
    super.afterEach()
    closeables.closeAll()
  }

  "header interceptor" should {
    "catch the headers" in {
      // Generate a unique in-process server name.
      val serverName: String = InProcessServerBuilder.generateName();
      val serviceImpl = new PingServiceImpl()
      val service = PingServiceGrpc.bindService(serviceImpl, global)

      // declare a variable and interceptor to capture the headers
      var responseHeaders: Option[Metadata] = None
      def intercept(p: Ping): Unit = {
        responseHeaders = Option(GrpcHeadersInterceptor.REQUEST_META.get())
      }

      serviceImpl.registerInterceptor(intercept)

      closeables.register(
        InProcessServerBuilder
          .forName(serverName)
          .directExecutor()
          .addService(service)
          .intercept(GrpcHeadersInterceptor)
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

      val stub = PingServiceGrpc.blockingStub(channel)

      val key = "x-custom-header"
      val value = "value"
      val requestHeaders: Metadata = getHeaders((key, value))

      MetadataUtils
        .attachHeaders(stub, requestHeaders)
        .send(Ping("hi"))

      responseHeaders.isDefined shouldBe (true)
      GrpcHelpers.getStringHeader(responseHeaders.get, key) shouldBe (value)
    }
  }
}
