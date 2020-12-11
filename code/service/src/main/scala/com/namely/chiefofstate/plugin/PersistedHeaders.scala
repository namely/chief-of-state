/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.plugin

import com.google.protobuf.ByteString
import com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers.{Header, Headers}
import com.namely.protobuf.chiefofstate.v1.service.ProcessCommandRequest
import io.grpc.Metadata
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable

private[this] class PersistedHeaders(persistedHeaders: Seq[String]) extends PluginBase {

  import PersistedHeaders.logger

  override val pluginId: String = "persisted_headers.v1"

  override def run(processCommandRequest: ProcessCommandRequest,
                   metadata: Metadata
  ): Option[com.google.protobuf.any.Any] = {

    val capturedHeaders: mutable.ListBuffer[Header] = mutable.ListBuffer.empty[Header]

    persistedHeaders.foreach(key => {
      if (key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
        val bytesKey: Metadata.Key[Array[Byte]] = Metadata.Key.of(key, Metadata.BINARY_BYTE_MARSHALLER)
        val byteValues = metadata.getAll[Array[Byte]](bytesKey)

        if (byteValues != null) {
          byteValues.forEach(byteArray => {
            val byteString = ByteString.copyFrom(byteArray)
            logger.debug(s"persisting header=${key}, type=bytes")
            capturedHeaders.addOne(Header(key = key).withBytesValue(byteString))
          })
        }
      } else {
        val stringKey: Metadata.Key[String] = Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER)
        val stringValues = metadata.getAll[String](stringKey)

        if (stringValues != null) {
          stringValues.forEach(stringValue => {
            logger.debug(s"persisting header=$key, type=string, value=$stringValue")
            capturedHeaders.addOne(Header(key = key).withStringValue(stringValue))
          })
        }
      }
    })

    if (capturedHeaders.nonEmpty) {
      Some(com.google.protobuf.any.Any.pack(Headers().withHeaders(capturedHeaders.toSeq)))
    } else {
      None
    }
  }
}

object PersistedHeaders extends PluginFactory {

  val envName: String = "COS_WRITE_PERSISTED_HEADERS"

  val logger: Logger = LoggerFactory.getLogger(getClass)

  def persistedHeaders: Seq[String] =
    sys.env
      .get(envName)
      .map(_.split(",").map(_.trim).toSeq)
      .getOrElse(Seq.empty[String])

  override def apply(): PluginBase = new PersistedHeaders(persistedHeaders)
}
