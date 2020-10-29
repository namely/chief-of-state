package com.namely.chiefofstate.plugin

import com.namely.protobuf.chiefofstate.v1.service.ProcessCommandRequest
import io.grpc.Metadata

import scala.util.Try

object PluginRunner {
  implicit class PluginBaseImplicits(pluginBase: PluginBase) {
    /**
     * Given any value, runs the makeMeta function in a Scala Try block. In the case where there is some value, returns a
     * Map[String, com.google.protobuf.any.Any], otherwise returns an empty String.
     *
     * @param processCommandRequest ProcessCommandRequest
     * @param metadata Grpc Metadata
     * @return `Try[Map[String, com.google.protobuf.any.Any]]`
     */
    final def run(processCommandRequest: ProcessCommandRequest, metadata: Metadata): Try[Map[String, com.google.protobuf.any.Any]] = {
      Try {
        pluginBase.makeAny(processCommandRequest, metadata) match {
          case Some(value) => Map(pluginBase.pluginId -> value)
          case None => Map.empty[String, com.google.protobuf.any.Any]
        }
      }
    }
  }
}
