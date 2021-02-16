package com.namely.chiefofstate.telemetry

import com.namely.chiefofstate.helper.BaseSpec
import io.opentelemetry.api.trace.{Span, Tracer}
import io.opentelemetry.api.{DefaultOpenTelemetry, GlobalOpenTelemetry, OpenTelemetry}
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.extension.trace.propagation.B3Propagator

import java.util.concurrent.Executors
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

class TracedExecutorServiceSpec extends BaseSpec{

  def performTask(implicit ec: ExecutionContext): Future[(Seq[String], Map[String, String])] = {
    val childSpanIds: mutable.ListBuffer[String] = new mutable.ListBuffer()

    val parentMap: mutable.HashMap[String, String] = new mutable.HashMap[String, String]()
    val tracer = GlobalOpenTelemetry.getTracer("testTracer")


    // run a future, map through another future, and start spans in each one
    Future { childSpanIds.clear() }
      .map(_ => {
        val parentSpan = Span.current()
        val childSpan = tracer.spanBuilder("oh we tracing").startSpan()
        val childSpanID = childSpan.getSpanContext.getSpanIdAsHexString
        childSpanIds.append(childSpanID)
        parentMap.update(childSpanID, parentSpan.getSpanContext.getSpanIdAsHexString)
        childSpan.end()
      })
      .map(_ => {
        val parentSpan = Span.current()
        val childSpan = tracer.spanBuilder("still tracing").startSpan()
        val childSpanID = childSpan.getSpanContext.getSpanIdAsHexString
        childSpanIds.append(childSpanID)
        parentMap.update(childSpanID, parentSpan.getSpanContext.getSpanIdAsHexString)
        childSpan.end()
      })
      .map(_ => Thread.sleep(100))
      .map(_ => (childSpanIds.toSeq, parentMap.toMap))
  }
  "future callables" should {
    "propagate the context when using executor service" in {

      implicit val ec: ExecutionContext = TracedExecutorService.get()

      val propagators: ContextPropagators = ContextPropagators.create(B3Propagator.builder.injectMultipleHeaders.build)
      val ot: OpenTelemetry = DefaultOpenTelemetry
        .builder()
        .setPropagators(propagators)
        .build()
      GlobalOpenTelemetry.set(ot)
      val tracer: Tracer = ot.getTracer("testTracer")

      val span = tracer.spanBuilder("outer span")
        .startSpan()

      val scope = span.makeCurrent()

      val futureTask = performTask(ec)

      futureTask.onComplete(_ => {
        scope.close()
        span.end()
      })

      whenReady(futureTask)( ret => {
        val childSpanIds = ret._1
        val parentMap = ret._2

        childSpanIds.length shouldBe 2

        // assert that the first span is the parent of second and third span
        childSpanIds.foreach(childSpanId => {
          parentMap(childSpanId) shouldBe span.getSpanContext.getSpanIdAsHexString
        })
      })
    }
    "not auto propagate the span on a normal execution context" in {
      val threadPool = Executors.newFixedThreadPool(1)
      implicit val ec = ExecutionContext.fromExecutor(threadPool)
      val propagators: ContextPropagators = ContextPropagators.create(B3Propagator.builder.injectMultipleHeaders.build)
      val ot: OpenTelemetry = DefaultOpenTelemetry
        .builder()
        .setPropagators(propagators)
        .build()
      GlobalOpenTelemetry.set(ot)
      val tracer: Tracer = ot.getTracer("testTracer")

      val span = tracer.spanBuilder("outer span")
        .startSpan()

      val scope = span.makeCurrent()

      val futureTask = performTask(ec)

      futureTask.onComplete(_ => {
        scope.close()
        span.end()
      })

      whenReady(futureTask)( ret => {
        val childSpanIds = ret._1
        val parentMap = ret._2

        childSpanIds.length shouldBe 2

        // assert that the first span is the parent of second and third span
        childSpanIds.foreach(childSpanId => {
          parentMap(childSpanId) shouldNot be(span.getSpanContext.getSpanIdAsHexString)
        })
      })
    }
  }

}
