package com.namely.chiefofstate.interceptors

import io.opentracing.util.GlobalTracer
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import io.opentracing.propagation.TextMapAdapter
import io.opentracing.propagation.Format
import io.opentracing.Tracer
import io.opentracing.SpanContext
import io.opentracing.Tracer.SpanBuilder
import scala.util.{Failure, Success, Try}
import io.opentracing.tag.Tags
import org.slf4j.{Logger, LoggerFactory}
import io.opentracing.log.Fields
import java.io.StringWriter
import java.io.PrintWriter

object OpentracingHelpers {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * inject the current trace/span information into a string
   * text map and return as a scala map
   *
   * @return a text map with all tracing information
   */
  def getTracingHeaders(): Map[String, String] = {
    val carrierMap = mutable.HashMap.empty[String, String].asJava
    val carrier: TextMapAdapter = new TextMapAdapter(carrierMap)

    Option(GlobalTracer.get()) match {
      case Some(tracer) if tracer.activeSpan != null =>
        tracer.inject(tracer.activeSpan.context, Format.Builtin.TEXT_MAP, carrier)
        carrierMap.asScala.toMap

      case _ =>
        log.warn("missing global tracer or span")
        Map.empty[String, String]
    }

  }

  /**
   * create a span context given remote text map headers
   *
   * @param tracer a Tracer to extract from
   * @param headers the map of remote headers
   * @return a parent span context
   */
  def getParentSpanContext(tracer: Tracer, headers: Map[String, String]): SpanContext = {
    val carrier: TextMapAdapter = new TextMapAdapter(headers.asJava)
    val parentSpanCtx: SpanContext = tracer.extract(Format.Builtin.TEXT_MAP, carrier)
    parentSpanCtx
  }

  /**
   * given the tracer, headers, and process name, yield a child span builder
   *
   * @param tracer a tracer, like the active one
   * @param headers headers from the parent span
   * @param processName a process name to generate a span for
   * @return a span builder for a child span
   */
  def getChildSpanBuilder(tracer: Tracer, headers: Map[String, String], processName: String): SpanBuilder = {
    val output = Try {

      var spanBuilder: SpanBuilder = tracer
        .buildSpan(processName)

      val parentSpan: Option[SpanContext] = Option(getParentSpanContext(tracer, headers))

      parentSpan
        .foreach(spanContext => spanBuilder = spanBuilder.asChildOf(spanContext))

      spanBuilder
    }

    output match {
      case Success(value) =>
        value

      case Failure(exception) =>
        log.error("failed to build child span", exception)
        tracer
          .buildSpan(processName)
          .withTag(Tags.ERROR.getKey(), true)

    }
  }

  /**
   * report an exception to the tracer
   *
   * @param tracer an opentracing tracer
   * @param exception an exception
   * @return Success if error reported
   */
  def reportErrorToTracer(tracer: Tracer, exception: Throwable): Try[Unit] = {

    if (tracer.activeSpan() == null) {
      log.warn(s"no active span to report errors")
      Failure(new Exception("no active span"))
    } else {
      Try {
        tracer.activeSpan.setTag(Tags.ERROR.getKey(), true)

        val sw = new StringWriter
        exception.printStackTrace(new PrintWriter(sw))

        val errMap: Map[String, String] = Map(
          Fields.EVENT -> "error",
          Fields.ERROR_KIND -> exception.getClass.getName,
          Fields.MESSAGE -> exception.getMessage(),
          Fields.STACK -> sw.toString()
        )

        tracer.activeSpan.log(errMap.asJava)
      }
    }
  }

  /**
   * reports a throwable to the global tracer
   *
   * @param exception an exception
   * @return  Success i ferror reported
   */
  def reportErrorToTracer(exception: Throwable): Try[Unit] = {
    reportErrorToTracer(GlobalTracer.get(), exception)
  }
}
