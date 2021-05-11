/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.config

import com.typesafe.config.Config
import io.superflat.otel.tools.TelemetryConfig

object TelemetryConfigFactory {
  val otlpEndpointKey = "chiefofstate.telemetry.otlp_endpoint"
  val namespaceKey = "chiefofstate.telemetry.namespace"
  val propagatorKey = "chiefofstate.telemetry.trace_propagators"
  private val serviceNameKey: String = "chiefofstate.service-name"

  def apply(config: Config): TelemetryConfig = {
    TelemetryConfig(
      config.getString(propagatorKey).split(',').toSeq,
      config.getString(otlpEndpointKey),
      config.getString(namespaceKey),
      config.getString(serviceNameKey))
  }
}
