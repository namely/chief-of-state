package com.namely.chiefofstate.plugin

import scala.util.Try

trait PluginBase {

  val pluginId: String

  /**
   * Given any value, runs the makeMeta function in a Scala Try block. In the case where there is some value, returns a
   * Map[String, com.google.protobuf.any.Any], otherwise returns an empty String.
   *
   * @param any: Any
   * @return `Try[Map[String, com.google.protobuf.any.Any]]`
   */
  final def run(any: Any): Try[Map[String, com.google.protobuf.any.Any]] = {
    Try {
      makeAny(any) match {
        case Some(value) => Map(pluginId -> value)
        case None => Map.empty[String, com.google.protobuf.any.Any]
      }
    }
  }

  /**
   * Abstract function to create an Option[com.google.protobuf.any.Any]
   *
   * @param any Any
   * @return Option[com.google.protobuf.any.Any]
   */
  def makeAny(any: Any): Option[com.google.protobuf.any.Any]
}
