/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.telemetry

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.{TextMapGetter, TextMapSetter}
import org.slf4j.{Logger, LoggerFactory}

import java.lang
import scala.collection.mutable
import scala.jdk.CollectionConverters._

object TracingHelpers {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  private class HashMapCarrier
      extends TextMapGetter[mutable.HashMap[String, String]]
      with TextMapSetter[mutable.HashMap[String, String]] {
    override def keys(carrier: mutable.HashMap[String, String]): lang.Iterable[String] = carrier.keys.toSeq.asJava

    override def get(carrier: mutable.HashMap[String, String], key: String): String = carrier.getOrElse(key, "")

    override def set(carrier: mutable.HashMap[String, String], key: String, value: String): Unit =
      carrier.update(key, value)
  }

  /**
   * inject the current trace/span information into a string
   * text map and return as a scala map
   *
   * @return a text map with all tracing information
   */
  def getTracingHeaders(ctx: Context): Map[String, String] = {
    val carrier = mutable.HashMap.empty[String, String]
    GlobalOpenTelemetry.getPropagators.getTextMapPropagator.inject(ctx, carrier, new HashMapCarrier())
    log.debug(s"Got Headers $carrier")
    carrier.toMap
  }

  /**
   * create a span context given remote text map headers
   *
   * @param tracer a Tracer to extract from
   * @param headers the map of remote headers
   * @return a parent span context
   */
  def getParentSpanContext(ctx: Context, headers: Map[String, String]): Context = {
    val carrier: mutable.HashMap[String, String] = headers.to(mutable.HashMap)
    GlobalOpenTelemetry.getPropagators.getTextMapPropagator.extract(ctx, carrier, new HashMapCarrier())
  }
}
