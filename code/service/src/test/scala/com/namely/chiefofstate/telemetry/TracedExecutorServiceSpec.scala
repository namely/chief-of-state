/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.telemetry

import com.namely.chiefofstate.helper.BaseSpec
import io.opentelemetry.api.{ GlobalOpenTelemetry, OpenTelemetry }
import io.opentelemetry.api.trace.{ Span, Tracer }
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.extension.trace.propagation.B3Propagator
import io.opentelemetry.sdk.OpenTelemetrySdk

import java.util.concurrent.Executors
import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.SdkTracerProvider
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._
class TracedExecutorServiceSpec extends BaseSpec {
  // create test OT and exporter that reset for each test
  var testExporter: InMemorySpanExporter = _
  var openTelemetry: OpenTelemetry = _

  // reset test OT between tests
  override def beforeEach(): Unit = {
    super.beforeEach()
    val propagators: ContextPropagators = ContextPropagators.create(B3Propagator.injectingMultiHeaders())
    // create a fresh in-mem test exporter
    testExporter = InMemorySpanExporter.create
    // create a trace provider from it
    val traceProvider = SdkTracerProvider.builder.addSpanProcessor(SimpleSpanProcessor.create(testExporter)).build
    // instantiate OT sdk using in-mem provider
    openTelemetry = OpenTelemetrySdk.builder.setTracerProvider(traceProvider).setPropagators(propagators).build()
  }

  "future callables" should {
    "trace when using the custom executor service" in {
      // create a traced EC
      implicit val ec: ExecutionContext = TracedExecutorService.get()
      // create a tracer, span, and scope
      val tracer: Tracer = openTelemetry.getTracer("parent tracer")
      val parentSpan = tracer.spanBuilder("outer span").startSpan()
      val parentScope = parentSpan.makeCurrent()
      // track spans
      val childSpans = new mutable.ListBuffer[Span]()
      // create child spans in a future
      val future = Future {
        val childSpan = tracer.spanBuilder("child span 1").startSpan()
        childSpans.addOne(childSpan)
        childSpan.end()
      }.map(_ => {
        val childSpan = tracer.spanBuilder("child span 2").startSpan()
        childSpans.addOne(childSpan)
        childSpan.end()
      })
      // end the parent scope & span
      parentScope.close()
      parentSpan.end()
      // await the future
      Await.ready(future, Duration.Inf)
      // ensure they were reported
      testExporter.flush()
      testExporter.getFinishedSpanItems().asScala.size shouldBe 3
      childSpans.size shouldBe 2
      // check that parent and children share a trace ID
      childSpans.foreach(_.getSpanContext().getTraceId() shouldBe parentSpan.getSpanContext().getTraceId())
    }
    "not trace when using a normal execution context" in {
      // create a normal EC
      val threadPool = Executors.newFixedThreadPool(1)
      implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(threadPool)
      // create a tracer, span, and scope
      val tracer: Tracer = openTelemetry.getTracer("parent tracer")
      val parentSpan = tracer.spanBuilder("outer span").startSpan()
      val parentScope = parentSpan.makeCurrent()
      // track spans
      val childSpans = new mutable.ListBuffer[Span]()
      // create child spans in a future
      val future = Future {
        val childSpan = tracer.spanBuilder("child span 1").startSpan()
        childSpans.addOne(childSpan)
        childSpan.end()
      }.map(_ => {
        val childSpan = tracer.spanBuilder("child span 2").startSpan()
        childSpans.addOne(childSpan)
        childSpan.end()
      })
      // end the parent scope & span
      parentScope.close()
      parentSpan.end()
      // await the future
      Await.ready(future, Duration.Inf)
      // ensure they were reported
      testExporter.flush()
      val actualSpans = testExporter.getFinishedSpanItems().asScala
      actualSpans.size shouldBe 3
      childSpans.size shouldBe 2
      // ensure all spans are on separate traces (disconnected)
      actualSpans.map(_.getSpanContext().getTraceId()).distinct.size shouldBe 3
    }
  }

}
