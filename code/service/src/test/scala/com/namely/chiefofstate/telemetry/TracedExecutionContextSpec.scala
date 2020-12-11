package com.namely.chiefofstate.telemetry

import com.namely.chiefofstate.helper.{AwaitHelper, BaseSpec}
import io.opentracing.mock.{MockSpan, MockTracer}
import io.opentracing.Span

import java.util.concurrent.Executors
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._

class TracedExecutionContextSpec extends BaseSpec {

  "future callables" should {
    "propagate the span in the wrapped context" in {
      val tracer: MockTracer = new MockTracer(MockTracer.Propagator.TEXT_MAP)
      tracer.reset()

      val span: Span = tracer
        .buildSpan("outer span")
        .ignoreActiveSpan()
        .start()

      tracer.activateSpan(span)

      // create the traced execution context
      implicit val ec = TracedExecutionContext.get(tracer)

      val childSpanIds: mutable.ListBuffer[Long] = new mutable.ListBuffer()

      // run a future, map through another future, and start spans in each one
      val someFuture = Future { childSpanIds.clear() }
        .map(_ => {
          val childSpan = tracer.buildSpan("oh we tracin").start()
          childSpanIds.append(childSpan.context().spanId())
          childSpan.finish()
        })
        .map(_ => {
          val childSpan = tracer.buildSpan("still tracin").start()
          childSpanIds.append(childSpan.context().spanId())
          childSpan.finish()
        })
        .map(_ => Thread.sleep(100))

      span.finish()

      whenReady(someFuture)(result => {
        val finishedSpans: Seq[MockSpan] = tracer
          .finishedSpans()
          .asScala
          .toSeq
          .sortBy(_.context().traceId())

        AwaitHelper.await(() => tracer.finishedSpans().size() >= 3, Duration(30, "sec"))

        finishedSpans.length shouldBe 3

        // construct a map of spans to their parent
        val parentMap: Map[Long, Long] = finishedSpans
          .map(span => span.context.spanId() -> span.parentId())
          .toMap

        // assert that the first span is the parent of second and third span
        childSpanIds.foreach(childSpanId => {
          parentMap(childSpanId) shouldBe span.context().toSpanId().toLong
        })
      })
    }

    "not auto propagate the span on a normal execution context" in {
      // make some random execution context
      val threadPool = Executors.newFixedThreadPool(1)
      implicit val ec = ExecutionContext.fromExecutor(threadPool)

      val tracer: MockTracer = new MockTracer(MockTracer.Propagator.TEXT_MAP)

      val span: Span = tracer
        .buildSpan("outer span")
        .ignoreActiveSpan()
        .start()

      tracer.activateSpan(span)

      val someFuture = Future(tracer.buildSpan("nope").start().finish())
        .map(_ => Thread.sleep(100))

      span.finish()

      AwaitHelper.await(() => tracer.finishedSpans().size() >= 2, Duration(30, "sec"))

      whenReady(someFuture)(result => {
        val finishedSpans: Seq[MockSpan] = tracer
          .finishedSpans()
          .asScala
          .toSeq
          .sortBy(_.context().traceId())

        finishedSpans.length shouldBe 2

        // assert that first span is not he parent of the second span
        finishedSpans(1).parentId() shouldNot be(finishedSpans(0).context.spanId())
      })

    }
  }
}
