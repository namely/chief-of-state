/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.telemetry

import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.sdk.metrics.`export`.IntervalMetricReader

import com.typesafe.config.Config
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.BatchSpanProcessor
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes

import java.util.Collections

case class TelemetryTools(config: Config) {

  def start(): Unit = {
    val propagators: ContextPropagators = PropagatorConfiguration
      .configurePropagators(config)

    val resource = configureResource(config)

    configureMetricsExporter(resource)

    val tracerProvider = configureProvider(config, resource)

    val sdkBuilder = OpenTelemetrySdk
      .builder()
      .setPropagators(propagators)

    tracerProvider.map(sdkBuilder.setTracerProvider)

    sdkBuilder.buildAndRegisterGlobal()
  }

  def configureMetricsExporter(resource: Resource): Unit = {
    val endpoint = config.getString("chiefofstate.otlp.endpoint")
    if (!endpoint.isBlank) {
      val meterProvider = SdkMeterProvider.builder.setResource(resource).buildAndRegisterGlobal
      val builder = OtlpGrpcMetricExporter.builder()

      builder.setEndpoint(endpoint)
      val exporter = builder.build
      val readerBuilder = IntervalMetricReader
        .builder
        .setMetricProducers(Collections.singleton(meterProvider))
        .setMetricExporter(exporter)
      val reader = readerBuilder.build
      sys.addShutdownHook {
        reader.shutdown()
      }
    }
  }

  def configureProvider(config: Config, resource: Resource): Option[SdkTracerProvider] = {
    Option(config.getString("chiefofstate.otlp.endpoint"))
      .filter(!_.isBlank)
      .map(endpoint => {
        val providerBuilder = SdkTracerProvider.builder()
        providerBuilder.setResource(resource)

        val exporter = OtlpGrpcSpanExporter.builder
          .setEndpoint(endpoint)
          .build()

        val processor = BatchSpanProcessor.builder(exporter).build()
        providerBuilder.addSpanProcessor(processor)

        val provider = providerBuilder.build()

        sys.addShutdownHook {
          provider.shutdown()
        }
        provider
      })
  }

  def configureResource(config: Config): Resource = {
    val resourceAttributes = Attributes.builder()
    resourceAttributes.put(ResourceAttributes.SERVICE_NAME, config.getString("chiefofstate.service-name"))

    Option(config.getString("chiefofstate.namespace"))
      .filter(!_.isBlank)
      .foreach(resourceAttributes.put(ResourceAttributes.SERVICE_NAMESPACE, _))

    Resource.getDefault.merge(Resource.create(resourceAttributes.build()))
  }

}
