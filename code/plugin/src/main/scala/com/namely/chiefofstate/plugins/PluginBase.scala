package com.namely.chiefofstate.plugins

import scala.util.Try

trait PluginBase {

  val pluginId: String

  /** Given any value, runs the makeMeta function in a Scala Try block. In the case where there is some value, returns a
   * Map[String, com.google.protobuf.any.Any], otherwise returns an empty String.
   *
   * @param any: Any
   * @return `Try[Map[String, com.google.protobuf.any.Any]]`
   */
  final def run(any: Any): Try[Map[String, com.google.protobuf.any.Any]] = {
    Try {
      makeMeta(any) match {
        case Some(value) => Map(pluginId -> value)
        case None => Map()
      }
    }
  }

  /** Abstract function to create an Option[com.google.protobuf.any.Any]
   *
   * @param any Any
   * @return Option[com.google.protobuf.any.Any]
   */
  def makeMeta(any: Any): Option[com.google.protobuf.any.Any]
}
