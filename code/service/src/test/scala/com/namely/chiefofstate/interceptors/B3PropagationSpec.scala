package com.namely.chiefofstate.interceptors

import com.namely.chiefofstate.helper.BaseSpec
import io.grpc.Metadata

import scala.util.Failure
import kamon.trace.Trace.SamplingDecision._
import kamon.trace.Span
import kamon.trace.Identifier
import kamon.trace.Trace

class B3PropagationSpec extends BaseSpec {
  ".spanFromHeaders" should {
    "create a span from gRPC metadata" in {
      val identifierScheme = kamon.Kamon.identifierScheme
      val headers = new Metadata()
      headers.put(B3Propagation.TraceIdentifier, "TraceIdentifier")
      headers.put(B3Propagation.SpanIdentifier, "SpanIdentifier")
      headers.put(B3Propagation.ParentSpanIdentifier, "ParentSpanIdentifier")
      headers.put(B3Propagation.Flags, "1")
      headers.put(B3Propagation.Sampled, "")
      val actual = B3Propagation.spanFromHeaders(headers)
      actual.id shouldBe identifierScheme.traceIdFactory.from("SpanIdentifier")
      actual.parentId shouldBe identifierScheme.traceIdFactory.from("ParentSpanIdentifier")
      actual.trace.id shouldBe identifierScheme.traceIdFactory.from("TraceIdentifier")
    }
    "handles all missing keys" in {
      val headers = new Metadata()
      val actual = B3Propagation.spanFromHeaders(headers)
      actual.id shouldBe (Identifier.Empty)
      actual.parentId shouldBe (Identifier.Empty)
    }
  }

  ".getSamplingDecision" should {
    "sample during debug" in {
      B3Propagation.getSamplingDecision(Some("1"), Some("1")) shouldBe Sample
    }
    "handle sampled" in {
      B3Propagation.getSamplingDecision(None, Some("1")) shouldBe Sample
    }
    "handle not sampled" in {
      B3Propagation.getSamplingDecision(None, Some("0")) shouldBe DoNotSample
    }
    "handle unknown decision" in {
      B3Propagation.getSamplingDecision(None, Some("🤷")) shouldBe Unknown
    }
  }

  ".updateHeadersWithSpan" should {
    "add trace and span headers" in {
      val headers: Metadata = new Metadata()

      val traceId = Identifier.Empty.copy(string = "trace-id")
      val trace = Trace(traceId, DoNotSample)

      val parentSpanId = Identifier.Empty.copy(string = "span-id")
      val spanId = Identifier.Empty.copy(string = "span-id")

      val parentSpan = Span.Remote(parentSpanId, Identifier.Empty, Trace.Empty)
      val span = Span.Remote(spanId, parentSpan.id, trace)

      headers.keys().isEmpty() shouldBe (true)

      B3Propagation.updateHeadersWithSpan(headers, span)

      headers.get(B3Propagation.TraceIdentifier) shouldBe (span.trace.id.string)
      headers.get(B3Propagation.SpanIdentifier) shouldBe (span.id.string)
      headers.get(B3Propagation.ParentSpanIdentifier) shouldBe spanId.string

    }
    "handle a null parent span" in {
      val headers: Metadata = new Metadata()

      val traceId = Identifier.Empty.copy(string = "trace-id")
      val trace = Trace(traceId, DoNotSample)

      val spanId = Identifier.Empty.copy(string = "span-id")

      val span = Span.Remote(spanId, Identifier.Empty, trace)

      headers.keys().isEmpty() shouldBe (true)

      B3Propagation.updateHeadersWithSpan(headers, span)

      headers.get(B3Propagation.TraceIdentifier) shouldBe (span.trace.id.string)
      headers.get(B3Propagation.SpanIdentifier) shouldBe (span.id.string)
      headers.get(B3Propagation.ParentSpanIdentifier) shouldBe null
    }
  }

  ".encodeSamplingDecision" should {
    "handle all sampling decisions" in {
      B3Propagation.encodeSamplingDecision(Sample) shouldBe Some("1")
      B3Propagation.encodeSamplingDecision(DoNotSample) shouldBe Some("0")
      B3Propagation.encodeSamplingDecision(Unknown) shouldBe None
    }
  }

}
