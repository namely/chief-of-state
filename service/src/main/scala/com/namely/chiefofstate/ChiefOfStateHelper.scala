package com.namely.chiefofstate

import com.google.protobuf.any.Any

import scala.util.{Failure, Success, Try}

/**
 * Utility methods
 */
object ChiefOfStateHelper {

  /**
   * Extracts the proto message package name
   *
   * @param proto the protocol buffer message
   * @return string
   */
  def getProtoFullyQualifiedName(proto: Any): String = {
    proto.typeUrl.split('/').lastOption.getOrElse("")
  }

  /**
   * Extracts read side configurations from environment variables
   *
   * @return `Map[String, Map[String, String]]`
   */
  def getReadSideConfigs: Map[String, Map[String, String]] = {

    val configurations: Map[String, Map[String, String]] = sys.env
      .filter(_._1.startsWith("COS_READSIDE_CONFIG__"))
      .groupMap(_._1.split("__").last)({ case(k, v) =>
        val settingNameAttempt: Try[String] = Try(k.split("__").tail.head)

        val settingName = settingNameAttempt match {
          case Success(name) => name
          case Failure(_) => throw new Exception(s"Invalid setting name for $k")
        }

        settingName -> v
      })
      .view
      .mapValues(_.toMap)
      .toMap

    configurations.foreach(x => {
      val settings: Set[String] = x._2.keySet
      require(settings.contains("HOST") && settings.contains("PORT"))
    })

    configurations
  }
}
