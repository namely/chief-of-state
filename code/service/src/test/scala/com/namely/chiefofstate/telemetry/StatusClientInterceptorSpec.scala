/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.telemetry

import com.namely.chiefofstate.helper.BaseSpec
import com.namely.protobuf.chiefofstate.test.helloworld.{GreeterGrpc, HelloReply, HelloRequest}
import com.namely.protobuf.chiefofstate.test.helloworld.GreeterGrpc.Greeter
import io.grpc.{ManagedChannel, ServerServiceDefinition, Status}
import io.grpc.inprocess.{InProcessChannelBuilder, InProcessServerBuilder}
import io.opentelemetry.api.{GlobalOpenTelemetry, OpenTelemetry}
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.instrumentation.grpc.v1_5.GrpcTracing
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future
import scala.util.Try

class StatusClientInterceptorSpec extends BaseSpec {

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
      val err: Throwable = Status.NOT_FOUND.withDescription("not found").asException()
      (serviceImpl.sayHello _)
        .expects(*)
        .returning(Future.failed(err))

      val service: ServerServiceDefinition = Greeter.bindService(serviceImpl, global)

      closeables.register(
        InProcessServerBuilder
          .forName(serverName)
          .directExecutor()
          .addService(service)
          .build()
          .start()
      )

      val statusInterceptor = new StatusClientInterceptor()
      val channel: ManagedChannel =
        InProcessChannelBuilder
          .forName(serverName)
          .directExecutor()
          .intercept(
            GrpcTracing.create(GlobalOpenTelemetry.get()).newClientInterceptor(),
            statusInterceptor
          )
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
      attributeData.get(AttributeKey.stringKey("grpc.kind")) shouldBe "client"
      attributeData.get(AttributeKey.stringKey("grpc.status_code")) shouldBe "NOT_FOUND"
      attributeData.get(AttributeKey.stringKey("grpc.ok")) shouldBe "false"

    }
  }
}
