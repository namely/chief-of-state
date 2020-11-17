package com.namely.chiefofstate.config

import com.typesafe.config.Config

/**
 * WriteHandler configuration
 *
 * @param host the gRPC host
 * @param port the gRPC port
 * @param eventsProtos the list of the events proto messages package names
 * @param statesProtos the list of the states proto messages package names
 * @param propagatedHeaders the list of gRPC headers to propagate
 */
case class WriteSideConfig(
  host: String,
  port: Int,
  enableProtoValidation: Boolean,
  eventsProtos: Seq[String],
  statesProtos: Seq[String],
  propagatedHeaders: Seq[String]
)

object WriteSideConfig {

  private val hostKey: String = "chiefofstate.write-side.host"
  private val portKey: String = "chiefofstate.write-side.port"
  private val protoValidationKey: String = "chiefofstate.write-side.enable-protos-validation"
  private val eventsProtosKey: String = "chiefofstate.write-side.events-protos"
  private val statesProtosKey: String = "chiefofstate.write-side.states-protos"
  private val propagatedHeadersKey: String = "chiefofstate.write-side.propagated-headers"

  /**
   * creates an instancee of WriteSideConfig
   *
   * @param config the configuration object
   * @return a new instance of WriteSideConfig
   */
  def apply(config: Config): WriteSideConfig = {

    WriteSideConfig(
      config.getString(hostKey),
      config.getInt(portKey),
      config.getBoolean(protoValidationKey),
      config
        .getString(eventsProtosKey)
        .trim
        .split(",")
        .toSeq
        .map(_.trim)
        .filter(_.nonEmpty),
      config
        .getString(statesProtosKey)
        .trim
        .split(",")
        .toSeq
        .map(_.trim)
        .filter(_.nonEmpty),
      config
        .getString(propagatedHeadersKey)
        .trim
        .split(",")
        .toSeq
        .map(_.trim)
        .filter(_.nonEmpty)
    )
  }
}
