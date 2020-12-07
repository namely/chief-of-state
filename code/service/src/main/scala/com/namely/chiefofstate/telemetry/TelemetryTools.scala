/*
 * MIT License
 *
 * Copyright (c) 2020 Namely
 */

package com.namely.chiefofstate.telemetry

import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.micrometer.prometheus.PrometheusConfig
import io.opentracing.contrib.metrics.micrometer.MicrometerMetricsReporter
import com.namely.chiefofstate.telemetry.PrometheusServer
import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer

case class TelemetryTools(config: Config, enableJaeger: Boolean, serviceName: String) {

  val GRPC_STATUS_LABEL: String = "grpc.status"

  val logger: Logger = LoggerFactory.getLogger(getClass)
  // create a composite registry
  val compositeRegistry = new CompositeMeterRegistry()
  // create prometheus registry
  val prometheusRegistry: PrometheusMeterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
  compositeRegistry.add(prometheusRegistry)
  // create metrics reporter
  val metricsReporter: MicrometerMetricsReporter = MicrometerMetricsReporter
    .newMetricsReporter()
    .withRegistry(compositeRegistry)
    .withName(serviceName)
    .withTagLabel(GRPC_STATUS_LABEL, "")
    .build()

  var tracer: Tracer = io.opentracing.noop.NoopTracerFactory.create()

  if (enableJaeger) {
    // create & register jaeger tracer
    tracer = io.jaegertracing.Configuration.fromEnv().getTracer
  }

  // wrap tracer for metrics
  tracer = io.opentracing.contrib.metrics.Metrics.decorate(tracer, metricsReporter)
  // register tracer globally
  GlobalTracer.registerIfAbsent(tracer)
  // create prometheus scraping server
  val prometheusServer: PrometheusServer = PrometheusServer(prometheusRegistry, config)

  /**
   * start all telemetry tools
   *
   * @return
   */
  def start(): TelemetryTools = {
    logger.debug("starting telemetry tools")
    this.prometheusServer.start()

    sys.addShutdownHook {
      stop()
    }

    this
  }

  /**
   * stop all telemetry tools
   */
  def stop(): Unit = {
    logger.debug("stopping telemetry tools")
    this.prometheusServer.stop()
  }
}
