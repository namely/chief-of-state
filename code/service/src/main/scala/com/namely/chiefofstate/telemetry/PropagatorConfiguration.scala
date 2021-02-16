/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.telemetry

import com.typesafe.config.Config
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.{ContextPropagators, TextMapPropagator}
import io.opentelemetry.extension.trace.propagation.{B3Propagator, JaegerPropagator, OtTracePropagator}

import scala.jdk.CollectionConverters._

object PropagatorConfiguration {
  def configurePropagators(config: Config): ContextPropagators = {
    val propagators: Seq[TextMapPropagator] = config
      .getString("chiefofstate.traces.propagator")
      .split(',')
      .toSeq
      .map(getPropagator)
    ContextPropagators.create(TextMapPropagator.composite(propagators.asJava))
  }

  private def getPropagator(name: String): TextMapPropagator = {
    name match {
      case "tracecontext" => W3CTraceContextPropagator.getInstance
      case "baggage"      => W3CBaggagePropagator.getInstance
      case "b3"           => B3Propagator.getInstance
      case "b3multi"      => B3Propagator.builder.injectMultipleHeaders.build
      case "jaeger"       => JaegerPropagator.getInstance
      case "ottracer"     => OtTracePropagator.getInstance
      case _ =>
        throw new RuntimeException(s"Unrecognized value for trace propagators: $name")
    }
  }

}
