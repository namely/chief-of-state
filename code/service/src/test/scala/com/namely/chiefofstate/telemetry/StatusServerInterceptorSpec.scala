/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.telemetry

import com.namely.chiefofstate.helper.BaseSpec
import com.namely.protobuf.chiefofstate.test.helloworld.GreeterGrpc.Greeter
import com.namely.protobuf.chiefofstate.test.helloworld.{GreeterGrpc, HelloReply, HelloRequest}
import io.grpc.inprocess.{InProcessChannelBuilder, InProcessServerBuilder}
import io.grpc.{ManagedChannel, ServerServiceDefinition, Status}
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.{GlobalOpenTelemetry, OpenTelemetry}
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.instrumentation.grpc.v1_5.GrpcTracing
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future
import scala.util.Try

class StatusServerInterceptorSpec extends BaseSpec {

  var testExporter: InMemorySpanExporter = _
  var openTelemetry: OpenTelemetry = _

  override def beforeEach(): Unit = {
    GlobalOpenTelemetry.resetForTest()

    testExporter = InMemorySpanExporter.create
    openTelemetry = OpenTelemetrySdk.builder
      .setTracerProvider(SdkTracerProvider.builder.addSpanProcessor(SimpleSpanProcessor.create(testExporter)).build)
      .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance))
      .buildAndRegisterGlobal
  }

  "interceptor" should {
    "add gRPC status code to span" in {
      val serverName: String = InProcessServerBuilder.generateName();
      val serviceImpl: Greeter = mock[Greeter]
      val err: Throwable = Status.PERMISSION_DENIED.withDescription("forbidden").asException()
      (serviceImpl.sayHello _)
        .expects(*)
        .returning(Future.failed(err))

      val service: ServerServiceDefinition = Greeter.bindService(serviceImpl, global)
      val statusInterceptor = new StatusServerInterceptor()

      closeables.register(
        InProcessServerBuilder
          .forName(serverName)
          .directExecutor()
          .addService(service)
          .intercept(GrpcTracing.create(openTelemetry).newServerInterceptor())
          .intercept(statusInterceptor)
          .build()
          .start()
      )

      val channel: ManagedChannel =
        InProcessChannelBuilder
          .forName(serverName)
          .directExecutor()
          .build()

      closeables.register(channel)

      val stub: GreeterGrpc.GreeterBlockingStub = GreeterGrpc.blockingStub(channel)

      val span: Span = openTelemetry
        .getTracer("test")
        .spanBuilder("foo")
        .startSpan()

      val scope = span.makeCurrent()

      val response: Try[HelloReply] = Try(stub.sayHello(HelloRequest("foo")))

      scope.close()
      span.end()

      response.isFailure shouldBe true

      val spans = testExporter.getFinishedSpanItems
      spans.size() shouldBe 2
      val attributeData = spans.get(0).getAttributes
      attributeData.get(AttributeKey.stringKey("grpc.kind")) shouldBe "server"
      attributeData.get(AttributeKey.stringKey("grpc.status_code")) shouldBe "PERMISSION_DENIED"
      attributeData.get(AttributeKey.stringKey("grpc.ok")) shouldBe "false"
    }
  }
}
