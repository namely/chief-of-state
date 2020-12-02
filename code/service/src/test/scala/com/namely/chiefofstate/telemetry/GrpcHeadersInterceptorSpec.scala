package com.namely.chiefofstate.common.telemetry

import com.namely.protobuf.reportbuilder.v1.helloworld.{GreeterGrpc, HelloReply, HelloRequest}
import com.namely.protobuf.reportbuilder.v1.helloworld.GreeterGrpc.Greeter
import com.namely.chiefofstate.common.{GrpcHelpers, TestSpec}
import io.grpc.inprocess.{InProcessChannelBuilder, InProcessServerBuilder}
import io.grpc.stub.MetadataUtils
import io.grpc.{ManagedChannel, Metadata}

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class GrpcHeadersInterceptorSpec extends TestSpec {
  import GrpcHelpers._

  // define set of resources to close after each test
  override val closeables: Closeables = new Closeables()

  override protected def afterEach(): Unit = {
    super.afterEach()
    closeables.closeAll()
  }

  "header interceptor" should {
    "catch the headers" in {
      // Generate a unique in-process server name.
      val serverName: String = InProcessServerBuilder.generateName();
      val serviceImpl = mock[Greeter]

      // declare a variable and interceptor to capture the headers
      var responseHeaders: Option[Metadata] = None

      (serviceImpl.sayHello _)
        .expects(*)
        .onCall { hello: HelloRequest =>
          {
            responseHeaders = Option(GrpcHeadersInterceptor.REQUEST_META.get())
            Future.successful(HelloReply().withMessage(hello.name))
          }
        }

      val service = GreeterGrpc.bindService(serviceImpl, global)

      closeables.register(
        InProcessServerBuilder
          .forName(serverName)
          .directExecutor()
          .addService(service)
          .intercept(GrpcHeadersInterceptor)
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

      val stub = GreeterGrpc.blockingStub(channel)

      val key = "x-custom-header"
      val value = "value"
      val requestHeaders: Metadata = getHeaders((key, value))

      MetadataUtils
        .attachHeaders(stub, requestHeaders)
        .sayHello(HelloRequest("hi"))

      responseHeaders.isDefined shouldBe true
      GrpcHelpers.getStringHeader(responseHeaders.get, key) shouldBe value
    }
  }
}
