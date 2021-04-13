/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.telemetry

import com.namely.chiefofstate.helper.{ BaseSpec, GrpcHelpers }
import com.namely.protobuf.chiefofstate.test.helloworld.{ GreeterGrpc, HelloReply, HelloRequest }
import com.namely.protobuf.chiefofstate.test.helloworld.GreeterGrpc.Greeter
import io.grpc.{ ManagedChannel, Metadata }
import io.grpc.inprocess.{ InProcessChannelBuilder, InProcessServerBuilder }
import io.grpc.stub.MetadataUtils

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class GrpcHeadersInterceptorSpec extends BaseSpec {
  import GrpcHelpers._

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

      (serviceImpl.sayHello _).expects(*).onCall { hello: HelloRequest =>
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
          .start())

      val channel: ManagedChannel =
        closeables.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())

      val stub = GreeterGrpc.blockingStub(channel)

      val key = "x-custom-header"
      val value = "value"
      val requestHeaders: Metadata = getHeaders((key, value))

      MetadataUtils.attachHeaders(stub, requestHeaders).sayHello(HelloRequest("hi"))

      responseHeaders.isDefined shouldBe true
      GrpcHelpers.getStringHeader(responseHeaders.get, key) shouldBe value
    }
  }
}
