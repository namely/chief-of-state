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

case class TelemetryTools(config: Config, serviceName: String) {
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
    .build()
  // create & register jaeger tracer
  val jaegerTracer: Tracer = io.jaegertracing.Configuration.fromEnv().getTracer
  // wrap tracer for metrics
  val tracer: Tracer = io.opentracing.contrib.metrics.Metrics.decorate(jaegerTracer, metricsReporter)
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
