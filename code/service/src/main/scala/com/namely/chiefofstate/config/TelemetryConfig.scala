package com.namely.chiefofstate.config

import com.typesafe.config.Config

case class TelemetryConfig(
  propagators: Seq[String],
  otlpEndpoint: String,
  namespace: String
)

object TelemetryConfig {
  val otlpEndpointKey = "chiefofstate.telemetry.otlp_endpoint"
  val namespaceKey = "chiefofstate.telemetry.namespace"
  val propagatorKey = "chiefofstate.telemetry.trace_propagators"

  def apply(config: Config): TelemetryConfig = {
    new TelemetryConfig(
      config.getString(propagatorKey).split(',').toSeq,
      config.getString(otlpEndpointKey),
      config.getString(namespaceKey)
    )
  }
}
