/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.readside

import com.google.protobuf.any
import com.namely.chiefofstate.helper.BaseSpec
import com.namely.protobuf.chiefofstate.v1.common.MetaData
import com.namely.protobuf.chiefofstate.v1.readside.{
  HandleReadSideRequest,
  HandleReadSideResponse,
  ReadSideHandlerServiceGrpc
}
import com.namely.protobuf.chiefofstate.v1.readside.ReadSideHandlerServiceGrpc.ReadSideHandlerServiceBlockingStub
import com.namely.protobuf.chiefofstate.v1.tests.{ Account, AccountOpened }
import io.grpc.Status
import io.grpc.inprocess._
import io.opentelemetry.api.{ GlobalOpenTelemetry, OpenTelemetry }
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk

import scala.jdk.CollectionConverters.ListHasAsScala
import scala.concurrent.ExecutionContext.global
import scala.concurrent.{ Await, Awaitable, CanAwait, ExecutionContext, Future }
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator

import java.util.concurrent.Executors
import scala.concurrent.duration.Duration

class ReadSideHandlerSpec extends BaseSpec {

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

  "ReadSideHandlerImpl" should {
    "handle events as expected" in {
      val accountOpened = AccountOpened()
      val account = Account()
      val eventTag = "chiefofstate8"
      val resultingState =
        com.google.protobuf.any.Any.pack(account.withBalance(200))

      val meta: MetaData = MetaData().withEntityId("231")

      val request: HandleReadSideRequest = HandleReadSideRequest()
        .withEvent(com.google.protobuf.any.Any.pack(accountOpened))
        .withState(resultingState)
        .withMeta(meta)

      val expected: HandleReadSideResponse = HandleReadSideResponse().withSuccessful(true)

      // mock the grpc server
      val mockImpl = mock[ReadSideHandlerServiceGrpc.ReadSideHandlerService]

      (mockImpl.handleReadSide _).expects(request).returning(Future.successful(expected))

      val service = ReadSideHandlerServiceGrpc.bindService(mockImpl, global)

      val serverName = InProcessServerBuilder.generateName()

      // register a server that intercepts traces and reports errors
      closeables.register(
        InProcessServerBuilder.forName(serverName).directExecutor().addService(service).build().start())

      val serverChannel = {
        closeables.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
      }

      val readSideHandlerServiceStub: ReadSideHandlerServiceBlockingStub =
        new ReadSideHandlerServiceBlockingStub(serverChannel)

      val readSideHandlerImpl = new ReadSideHandlerImpl("id", readSideHandlerServiceStub)

      val triedHandleReadSideResponse =
        readSideHandlerImpl.processEvent(
          event = com.google.protobuf.any.Any.pack(accountOpened),
          eventTag = eventTag,
          resultingState = resultingState,
          meta = meta,
          maxAttempts = 5)

      triedHandleReadSideResponse shouldBe true
    }

    "handle response with explicit failure" in {
      val accountOpened = AccountOpened()
      val account = Account()
      val eventTag = "chiefofstate8"
      val resultingState =
        com.google.protobuf.any.Any.pack(account.withBalance(200))

      val meta: MetaData = MetaData().withEntityId("231")

      val request: HandleReadSideRequest = HandleReadSideRequest()
        .withEvent(com.google.protobuf.any.Any.pack(accountOpened))
        .withState(resultingState)
        .withMeta(meta)

      val expected: HandleReadSideResponse = HandleReadSideResponse().withSuccessful(false)

      // mock the grpc server
      val mockImpl = mock[ReadSideHandlerServiceGrpc.ReadSideHandlerService]

      (mockImpl.handleReadSide _).expects(request).returning(Future.successful(expected))

      val service = ReadSideHandlerServiceGrpc.bindService(mockImpl, global)

      val serverName = InProcessServerBuilder.generateName()

      // register a server that intercepts traces and reports errors
      closeables.register(
        InProcessServerBuilder.forName(serverName).directExecutor().addService(service).build().start())

      val serverChannel = {
        closeables.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
      }

      val readSideHandlerServiceStub: ReadSideHandlerServiceBlockingStub =
        new ReadSideHandlerServiceBlockingStub(serverChannel)

      val readSideHandlerImpl = new ReadSideHandlerImpl("id", readSideHandlerServiceStub)

      val triedHandleReadSideResponse =
        readSideHandlerImpl.processEvent(
          event = com.google.protobuf.any.Any.pack(accountOpened),
          eventTag = eventTag,
          resultingState = resultingState,
          meta = meta,
          maxAttempts = 5)

      triedHandleReadSideResponse shouldBe false
    }

    "handle event when there is an exception" in {
      val accountOpened = AccountOpened()
      val account = Account()
      val eventTag = "chiefofstate8"
      val resultingState =
        com.google.protobuf.any.Any.pack(account.withBalance(200))

      val meta: MetaData = MetaData().withEntityId("231")

      val request: HandleReadSideRequest = HandleReadSideRequest()
        .withEvent(com.google.protobuf.any.Any.pack(accountOpened))
        .withState(resultingState)
        .withMeta(meta)

      val mockImpl = mock[ReadSideHandlerServiceGrpc.ReadSideHandlerService]

      val expectedError = Status.INTERNAL.asRuntimeException()
      (mockImpl.handleReadSide _).expects(request).returning(Future.failed(expectedError))

      val service = ReadSideHandlerServiceGrpc.bindService(mockImpl, global)

      val serverName = InProcessServerBuilder.generateName()

      // register a server that intercepts traces and reports errors
      closeables.register(
        InProcessServerBuilder.forName(serverName).directExecutor().addService(service).build().start())

      val serverChannel = {
        closeables.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
      }

      val readSideHandlerServiceStub: ReadSideHandlerServiceBlockingStub =
        new ReadSideHandlerServiceBlockingStub(serverChannel)

      val readSideHandlerImpl = new ReadSideHandlerImpl("id", readSideHandlerServiceStub)
      val triedHandleReadSideResponse =
        readSideHandlerImpl.processEvent(
          event = com.google.protobuf.any.Any.pack(accountOpened),
          eventTag = eventTag,
          resultingState = resultingState,
          meta = meta,
          maxAttempts = 5)

      triedHandleReadSideResponse shouldBe false

      // assert the span was closed even in case of a failure
      testExporter.getFinishedSpanItems.asScala.exists(_.getName == readSideHandlerImpl.spanName) shouldBe true
    }
  }

  "ReadSideHandler" should {
    "run up to the maxAttempts" in {
      val readSideHandler: MockReadSideHandler = new MockReadSideHandler
      val actual: Boolean = readSideHandler.processEvent(null, null, null, null, maxAttempts = 1)

      actual shouldBe false
      readSideHandler.getNumRuns should be > 0L
      readSideHandler.isStarted shouldBe true
      readSideHandler.isEnded shouldBe true
    }

    "run indefinitely" in {
      val readSideHandler: MockReadSideHandler = new MockReadSideHandler

      val ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1))
      val f = new Cancellable[Boolean](ec, readSideHandler.processEvent(null, null, null, null, maxBackoffSeconds = 1))

      Thread.sleep(2000) // 2 seconds

      f.cancel()

      println(readSideHandler.getNumRuns)
      readSideHandler.getNumRuns should be > 1L
      readSideHandler.isStarted shouldBe true
      readSideHandler.isEnded shouldBe false
    }

    "fail minBackoffSeconds requirement" in {
      val readSideHandler: MockReadSideHandler = new MockReadSideHandler
      val err: IllegalArgumentException = intercept[IllegalArgumentException] {
        readSideHandler.processEvent(null, null, null, null, minBackoffSeconds = 0)
      }
      err.getMessage shouldBe "requirement failed: minBackOffSeconds must be greater than 0"
    }

    "fail maxBackoffSeconds requirement" in {
      val readSideHandler: MockReadSideHandler = new MockReadSideHandler
      val err: IllegalArgumentException = intercept[IllegalArgumentException] {
        readSideHandler.processEvent(null, null, null, null, minBackoffSeconds = 100, maxBackoffSeconds = 0)
      }
      err.getMessage shouldBe "requirement failed: maxBackOffSeconds must be greater than or equal to minBackOffSeconds"
    }
  }
}

class MockReadSideHandler extends ReadSideHandler {
  private var started: Boolean = false
  private var ended: Boolean = false
  private var numRuns: Long = 0

  def isStarted: Boolean = started
  def isEnded: Boolean = ended
  def getNumRuns: Long = numRuns

  override def onBeginProcess(): Unit = { started = true }
  override def onEndProcess(): Unit = { ended = true }
  override protected def doProcessEvent(
      event: any.Any,
      eventTag: String,
      resultingState: any.Any,
      meta: MetaData): Boolean = {
    numRuns = numRuns + 1L
    false
  }
}
