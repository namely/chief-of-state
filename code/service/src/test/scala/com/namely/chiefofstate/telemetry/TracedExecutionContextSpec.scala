package com.namely.chiefofstate.common.telemetry

import com.namely.chiefofstate.common.{AwaitHelper, TestSpec}
import io.opentracing.mock.MockTracer
import io.opentracing.mock.MockSpan
import scala.jdk.CollectionConverters._
import scala.concurrent.Future
import io.opentracing.Span
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors
import scala.concurrent.duration.Duration

class TracedExecutionContextSpec extends TestSpec {

  "future callables" should {
    "propagate the span in the wrapped context" in {
      val tracer: MockTracer = new MockTracer(MockTracer.Propagator.TEXT_MAP)

      val span: Span = tracer
        .buildSpan("outer span")
        .ignoreActiveSpan()
        .start()

      tracer.activateSpan(span)

      // create the traced execution context
      implicit val ec = TracedExecutionContext.get(tracer)

      // run a future, map through another future, and start spans in each one
      val someFuture = Future(tracer.buildSpan("oh we tracin").start().finish())
        .map(_ => tracer.buildSpan("still tracin").start().finish())

      span.finish()

      whenReady(someFuture)(result => {
        val finishedSpans: Seq[MockSpan] = tracer
          .finishedSpans()
          .asScala
          .toSeq
          .sortBy(_.context().traceId())

        AwaitHelper.await(() => tracer.finishedSpans().size() >= 3, Duration(30, "sec"))

        finishedSpans.length shouldBe 3

        // assert that the first span is the parent of second and third span
        finishedSpans(1).parentId() shouldBe (finishedSpans(0).context.spanId())
        finishedSpans(2).parentId() shouldBe (finishedSpans(0).context.spanId())
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
