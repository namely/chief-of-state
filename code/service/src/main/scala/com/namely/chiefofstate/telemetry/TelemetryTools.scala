/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.telemetry

import com.namely.chiefofstate.config.CosConfig
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.metrics.`export`.IntervalMetricReader
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.BatchSpanProcessor
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes

import java.util.Collections

case class TelemetryTools(config: CosConfig) {

  /**
   * Create an open telemetry SDK and configure it to instrument the service for trace and metrics collection.
   *
   *  This sets the created SDK as the Global Open Telemetry instance enabling use of the global tracer.
   */
  def start(): Unit = {
    val propagators: ContextPropagators = PropagatorConfiguration
      .configurePropagators(config.telemetryConfig)

    val resource = configureResource(config)

    configureMetricsExporter(resource)

    val tracerProvider = configureProvider(config, resource)

    val sdkBuilder = OpenTelemetrySdk
      .builder()
      .setPropagators(propagators)

    tracerProvider.map(sdkBuilder.setTracerProvider)

    sdkBuilder.buildAndRegisterGlobal()
  }

  /**
   * Configure a metrics exporter.
   * @param resource a Resource instance representing the instrumented target.
   */
  def configureMetricsExporter(resource: Resource): Unit = {
    val endpoint: String = config.telemetryConfig.otlpEndpoint
    if (endpoint.nonEmpty) {
      val meterProvider = SdkMeterProvider.builder.setResource(resource).buildAndRegisterGlobal
      val builder = OtlpGrpcMetricExporter.builder()

      builder.setEndpoint(endpoint)
      val exporter = builder.build
      val readerBuilder = IntervalMetricReader.builder
        .setMetricProducers(Collections.singleton(meterProvider))
        .setMetricExporter(exporter)
      val reader = readerBuilder.build
      sys.addShutdownHook {
        reader.shutdown()
      }
    }
  }

  /**
   * Configure the Tracer Provider that will be used to process trace entries.
   *
   * @param config the config object which holds the provider settings
   * @param resource a Resource instance representing the instrumented target
   * @return a Tracer provider configured to export metrics using OTLP
   */
  def configureProvider(config: CosConfig, resource: Resource): Option[SdkTracerProvider] = {
    Option(config.telemetryConfig.otlpEndpoint)
      .filter(_.nonEmpty)
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

  /**
   * Create a resource instance that will hold details of the service being instrumented.
   *
   * @param config the config object to be used to extract the service details
   * @return a resource representing the current instrumentation target.
   */
  def configureResource(config: CosConfig): Resource = {
    val resourceAttributes = Attributes.builder()
    resourceAttributes.put(ResourceAttributes.SERVICE_NAME, config.serviceName)

    Option(config.telemetryConfig.namespace)
      .filter(_.nonEmpty)
      .foreach(resourceAttributes.put(ResourceAttributes.SERVICE_NAMESPACE, _))

    Resource.getDefault.merge(Resource.create(resourceAttributes.build()))
  }

}
