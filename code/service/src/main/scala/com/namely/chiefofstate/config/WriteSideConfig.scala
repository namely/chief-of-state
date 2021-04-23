/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.config

import com.typesafe.config.Config

/**
 * WriteHandler configuration
 *
 * @param host the gRPC host
 * @param port the gRPC port
 * @param useTls enable TLS For outbound write handler calls
 * @param eventsProtos the list of the events proto messages package names
 * @param statesProtos the list of the states proto messages package names
 * @param propagatedHeaders the list of gRPC headers to propagate
 * @param persistedHeaders the list of gRPC headers to persist
 */
case class WriteSideConfig(
    host: String,
    port: Int,
    useTls: Boolean,
    enableProtoValidation: Boolean,
    eventsProtos: Seq[String],
    statesProtos: Seq[String],
    propagatedHeaders: Seq[String],
    persistedHeaders: Seq[String])

object WriteSideConfig {

  private val hostKey: String = "chiefofstate.write-side.host"
  private val portKey: String = "chiefofstate.write-side.port"
  private val useTlsKey: String = "chiefofstate.write-side.use-tls"
  private val protoValidationKey: String = "chiefofstate.write-side.enable-protos-validation"
  private val eventsProtosKey: String = "chiefofstate.write-side.events-protos"
  private val statesProtosKey: String = "chiefofstate.write-side.states-protos"
  private val propagatedHeadersKey: String = "chiefofstate.write-side.propagated-headers"
  private val persistedHeadersKey: String = "chiefofstate.write-side.persisted-headers"

  /**
   * creates an instancee of WriteSideConfig
   *
   * @param config the configuration object
   * @return a new instance of WriteSideConfig
   */
  def apply(config: Config): WriteSideConfig = {

    WriteSideConfig(
      host = config.getString(hostKey),
      port = config.getInt(portKey),
      useTls = config.getBoolean(useTlsKey),
      enableProtoValidation = config.getBoolean(protoValidationKey),
      eventsProtos = csvSplitDistinct(config.getString(eventsProtosKey)),
      statesProtos = csvSplitDistinct(config.getString(statesProtosKey)),
      propagatedHeaders = csvSplitDistinct(config.getString(propagatedHeadersKey)),
      persistedHeaders = csvSplitDistinct(config.getString(persistedHeadersKey)))
  }

  /**
   * split config string on a comma
   *
   * @param s a string
   * @return a sequence of values
   */
  private def csvSplitDistinct(s: String): Seq[String] = {
    s.trim.split(",").toSeq.map(_.trim).filter(_.nonEmpty).distinct
  }
}
