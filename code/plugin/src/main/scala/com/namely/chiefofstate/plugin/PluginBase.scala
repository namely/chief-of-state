/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.plugin

import com.namely.protobuf.chiefofstate.v1.service.ProcessCommandRequest
import io.grpc.Metadata

/**
 * Interface for the Chief-of-state plugins
 */
trait PluginBase {

  def pluginId: String

  /**
   * Abstract function to create an Option[com.google.protobuf.any.Any]
   *
   * @param processCommandRequest ProcessCommandRequest
   * @param metadata Grpc Metadata
   * @return Option[com.google.protobuf.any.Any]
   */
  def run(processCommandRequest: ProcessCommandRequest, metadata: Metadata): Option[com.google.protobuf.any.Any]
}

/**
 * Factory of PluginBase
 */
trait PluginFactory {

  /**
   * Returns a PluginBase
   *
   * @return PluginBase instance
   */
  def apply(): PluginBase
}
