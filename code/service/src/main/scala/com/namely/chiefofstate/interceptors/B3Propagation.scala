package com.namely.chiefofstate.interceptors

import io.grpc.Metadata
import kamon.trace.Span
import kamon.Kamon
import kamon.trace.Trace.SamplingDecision
import kamon.trace.Identifier
import kamon.trace.Trace

object B3Propagation {
  val TraceIdentifier = Metadata.Key.of("X-B3-TraceId", Metadata.ASCII_STRING_MARSHALLER)
  val ParentSpanIdentifier = Metadata.Key.of("X-B3-ParentSpanId", Metadata.ASCII_STRING_MARSHALLER)
  val SpanIdentifier = Metadata.Key.of("X-B3-SpanId", Metadata.ASCII_STRING_MARSHALLER)
  val Sampled = Metadata.Key.of("X-B3-Sampled", Metadata.ASCII_STRING_MARSHALLER)
  val Flags = Metadata.Key.of("X-B3-Flags", Metadata.ASCII_STRING_MARSHALLER)

  def spanFromHeaders(headers: Metadata): Span = {
    val identifierScheme = Kamon.identifierScheme
    val traceID = Option(headers.get(TraceIdentifier))
      .map(id => identifierScheme.traceIdFactory.from(id))
      .getOrElse(Identifier.Empty)
    val spanID = Option(headers.get(SpanIdentifier))
      .map(id => identifierScheme.traceIdFactory.from(id))
      .getOrElse(Identifier.Empty)
    val parentID = Option(headers.get(ParentSpanIdentifier))
      .map(id => identifierScheme.traceIdFactory.from(id))
      .getOrElse(Identifier.Empty)
    val flags = Option(headers.get(Flags))
    val samplingDecision = flags match {
      case Some(debug) if debug == "1" => SamplingDecision.Sample
      case _ =>
        Option(headers.get(Sampled)) match {
          case Some(sampled) if sampled == "1" => SamplingDecision.Sample
          case Some(sampled) if sampled == "0" => SamplingDecision.DoNotSample
          case _                               => SamplingDecision.Unknown
        }
    }
    new Span.Remote(spanID, parentID, Trace(traceID, samplingDecision))
  }

  def updateHeadersWithSpan(headers: Metadata, span: Span): Unit = {
    headers.put(TraceIdentifier, span.trace.id.string)
    headers.put(SpanIdentifier, span.id.string)

    if (span.parentId != Identifier.Empty) {
      headers.put(ParentSpanIdentifier, span.parentId.string)
    }
    encodeSamplingDecision(span.trace.samplingDecision).map(decision => headers.put(Sampled, decision))
  }

  private def encodeSamplingDecision(samplingDecision: SamplingDecision): Option[String] =
    samplingDecision match {
      case SamplingDecision.Sample      => Some("1")
      case SamplingDecision.DoNotSample => Some("0")
      case SamplingDecision.Unknown     => None
    }

}
