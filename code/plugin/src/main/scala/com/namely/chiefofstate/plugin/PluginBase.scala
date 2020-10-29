package com.namely.chiefofstate.plugin

import com.namely.protobuf.chiefofstate.v1.service.ProcessCommandRequest
import io.grpc.Metadata

import scala.util.Try

/**
 * Interface for the Chief-of-state plugins
 */
trait PluginBase {

  def pluginId: String

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
      makeAny(processCommandRequest, metadata) match {
        case Some(value) => Map(pluginId -> value)
        case None => Map.empty[String, com.google.protobuf.any.Any]
      }
    }
  }

  /**
   * Abstract function to create an Option[com.google.protobuf.any.Any]
   *
   * @param processCommandRequest ProcessCommandRequest
   * @param metadata Grpc Metadata
   * @return Option[com.google.protobuf.any.Any]
   */
  def makeAny(processCommandRequest: ProcessCommandRequest, metadata: Metadata): Option[com.google.protobuf.any.Any]
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
