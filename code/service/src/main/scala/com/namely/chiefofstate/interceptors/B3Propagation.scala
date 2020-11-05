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

  /**
   * get a span from headers
   *
   * @param headers gRPC metadata
   * @return a span
   */
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
    val sampled = Option(headers.get(Sampled))
    val samplingDecision = getSamplingDecision(flags, sampled)

    new Span.Remote(spanID, parentID, Trace(traceID, samplingDecision))
  }

  /**
   * update headers with a span
   *
   * @param headers some gRPC metadata
   * @param span a span
   */
  def updateHeadersWithSpan(headers: Metadata, span: Span): Unit = {
    headers.put(TraceIdentifier, span.trace.id.string)
    headers.put(SpanIdentifier, span.id.string)

    if (span.parentId != Identifier.Empty) {
      headers.put(ParentSpanIdentifier, span.parentId.string)
    }
    encodeSamplingDecision(span.trace.samplingDecision).map(decision => headers.put(Sampled, decision))
  }

  /**
   * returns the string encoding of a sampling decision
   *
   * @param samplingDecision a sampling decision
   * @return option of string, 1 or 0
   */
  private[interceptors] def encodeSamplingDecision(samplingDecision: SamplingDecision): Option[String] = {
    samplingDecision match {
      case SamplingDecision.Sample      => Some("1")
      case SamplingDecision.DoNotSample => Some("0")
      case _                            => None
    }
  }

  /**
   * helper method to get a sampling decision given flags
   *
   * @param flags some flags from grpc headers
   * @param sampled sampled gRPC header
   * @return a sampling decision
   */
  private[interceptors] def getSamplingDecision(flags: Option[String], sampled: Option[String]): SamplingDecision = {
    flags match {
      // handle debug flag
      case Some("1") => SamplingDecision.Sample
      // else, use real sampled decision
      case _ =>
        sampled match {
          case Some("1") => SamplingDecision.Sample
          case Some("0") => SamplingDecision.DoNotSample
          case _         => SamplingDecision.Unknown
        }
    }
  }

}
