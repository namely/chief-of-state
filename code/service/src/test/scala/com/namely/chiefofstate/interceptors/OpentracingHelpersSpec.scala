package com.namely.chiefofstate.interceptors

import com.namely.chiefofstate.helper.BaseSpec
import io.grpc.stub.MetadataUtils
import io.grpc.Metadata
import io.opentracing.mock.MockSpan
import io.opentracing.mock.MockTracer
import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer
import io.opentracing.Tracer.SpanBuilder
import io.opentracing.tag.Tags
import scala.util.Try
import io.opentracing.log.Fields
import scala.jdk.CollectionConverters._
import scala.collection.mutable

class OpentracingHelpersSpec extends BaseSpec {

  val mockTracer: MockTracer = new MockTracer(MockTracer.Propagator.TEXT_MAP)
  GlobalTracer.registerIfAbsent(mockTracer)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    mockTracer.reset()
  }

  ".getTracingHeaders" should {
    "handle a null active span" in {
      val span: Span = mockTracer
        .buildSpan("foo")
        .start()

      GlobalTracer.isRegistered() shouldBe true

      val actual = OpentracingHelpers.getTracingHeaders()

      span.finish()

      actual.isEmpty shouldBe true
    }
    "yield a map with parent spanId and traceId" in {
      val span: Span = mockTracer
        .buildSpan("foo")
        .start()

      GlobalTracer.get().activateSpan(span)

      val actual = OpentracingHelpers.getTracingHeaders()
      span.finish()

      val finishedSpans = mockTracer.finishedSpans().asScala.toSeq
      finishedSpans.length shouldBe 1

      actual.get("spanid") shouldBe Some(span.context().toSpanId())
      actual.get("traceid") shouldBe Some(span.context().toTraceId())
    }
  }
  ".getParentSpanContext" should {
    "return a span context given the headers" in {
      val tracer: MockTracer = new MockTracer(MockTracer.Propagator.TEXT_MAP)
      val headers: Map[String, String] = Map("spanid" -> "1", "traceid" -> "2")
      val actual = OpentracingHelpers.getParentSpanContext(tracer, headers)
      actual.toSpanId() shouldBe "1"
      actual.toTraceId() shouldBe "2"
    }
  }
  ".getChildSpanBuilder" should {
    "return a spanBuilder connected to the parent" in {
      val span: Span = mockTracer
        .buildSpan("foo")
        .start()

      GlobalTracer.get().activateSpan(span)

      val headers: Map[String, String] = Map("spanid" -> "11", "traceid" -> "12")

      val actualBuilder: SpanBuilder = OpentracingHelpers
        .getChildSpanBuilder(mockTracer, headers, "foo")

      val actualSpan: Span = actualBuilder.start()

      actualSpan.finish()
      span.finish()

      val finishedSpans: Seq[MockSpan] = mockTracer.finishedSpans().asScala.toSeq

      finishedSpans.length shouldBe 2

      finishedSpans(1).context().toSpanId shouldBe span.context().toSpanId
      finishedSpans(0).context().spanId() shouldBe finishedSpans(1).context().spanId() + 1
    }
    "return a span even if no parent" in {
      val headers: Map[String, String] = Map("spanid" -> "11", "traceid" -> "12")

      val actualBuilder: SpanBuilder = OpentracingHelpers
        .getChildSpanBuilder(mockTracer, headers, "foo")

      val actualSpan: Span = actualBuilder.start()

      actualSpan.finish()

      val finishedSpans: Seq[MockSpan] = mockTracer.finishedSpans().asScala.toSeq

      finishedSpans.length shouldBe 1
      finishedSpans.head.context().toSpanId() shouldBe actualSpan.context().toSpanId()
    }
    "return a span from a failure" in {
      val tracer = mock[Tracer]

      var callCount: Int = 0

      val processName: String = "foo"
      val errMsg: String = "bar"

      // mock the method to fail the second time
      (tracer.buildSpan _)
        .expects(processName)
        .onCall { arg: String =>
          {
            callCount += 1
            if (callCount <= 1) throw new Exception(errMsg)
            mockTracer.buildSpan(processName)
          }
        }
        .anyNumberOfTimes

      val actualBuilder: SpanBuilder = OpentracingHelpers
        .getChildSpanBuilder(tracer, Map.empty[String, String], processName)

      // start and stop the span
      val actualSpan: Span = actualBuilder.start()
      actualSpan.finish()

      val finishedSpans: Seq[MockSpan] = mockTracer.finishedSpans().asScala.toSeq
      finishedSpans.length shouldBe 1

      val actualTags = finishedSpans.head.tags().asScala
      actualTags.get(Tags.ERROR.getKey()) shouldBe Some(true)
    }
  }
  ".reportErrorToTracer" should {
    "handle missing active span" in {
      val tracer: MockTracer = new MockTracer(MockTracer.Propagator.TEXT_MAP)
      tracer.reset()
      tracer.activeSpan() shouldBe null
      val e = new Exception("its broken")
      val actual: Try[Unit] = OpentracingHelpers.reportErrorToTracer(tracer, e)
      actual.isFailure shouldBe (true)
    }
    "tag active span with error" in {
      val span: Span = mockTracer
        .buildSpan("foo")
        .start()

      mockTracer.activateSpan(span)

      val e = new RuntimeException("its broken")
      val actual: Try[Unit] = OpentracingHelpers.reportErrorToTracer(mockTracer, e)

      span.finish()

      actual.isSuccess shouldBe true

      val finishedSpans = mockTracer.finishedSpans().asScala
      finishedSpans.size shouldBe 1

      val finishedSpan = finishedSpans.head
      val actualTags = finishedSpan.tags().asScala
      val actualLogs = finishedSpan.logEntries().asScala
      actualLogs.size shouldBe (1)
      val logMap = actualLogs.head.fields().asScala
      // assert he tags
      actualTags.get(Tags.ERROR.getKey()) shouldBe Some(true)
      // asser the logs
      logMap.get(Fields.ERROR_KIND) shouldBe Some(e.getClass.getName)
      logMap.get(Fields.MESSAGE) shouldBe Some(e.getMessage())
      logMap.get(Fields.STACK).isDefined shouldBe true
    }
    "default to global tracer" in {
      val span: Span = mockTracer
        .buildSpan("foo")
        .start()

      GlobalTracer.isRegistered() shouldBe true
      GlobalTracer.get().activateSpan(span)

      val e = new RuntimeException("its broken")
      val actual: Try[Unit] = OpentracingHelpers.reportErrorToTracer(e)

      span.finish()
      actual.isSuccess shouldBe true

      val finishedSpans = mockTracer.finishedSpans().asScala
      finishedSpans.size shouldBe 1

      val finishedSpan = finishedSpans.head
      val actualTags = finishedSpan.tags().asScala
      val actualLogs = finishedSpan.logEntries().asScala
      actualLogs.size shouldBe (1)
      val logMap = actualLogs.head.fields().asScala
      // assert he tags
      actualTags.get(Tags.ERROR.getKey()) shouldBe Some(true)
      // asser the logs
      logMap.get(Fields.ERROR_KIND) shouldBe Some(e.getClass.getName)
      logMap.get(Fields.MESSAGE) shouldBe Some(e.getMessage())
      logMap.get(Fields.STACK).isDefined shouldBe true
    }
  }
}
