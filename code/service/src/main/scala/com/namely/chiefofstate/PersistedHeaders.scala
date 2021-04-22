/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import com.google.protobuf.ByteString
import com.namely.protobuf.chiefofstate.v1.common.Header
import com.namely.protobuf.chiefofstate.v1.service.ProcessCommandRequest
import io.grpc.Metadata
import org.slf4j.{ Logger, LoggerFactory }

import scala.collection.mutable

object PersistedHeaders {

  val envName: String = "COS_WRITE_PERSISTED_HEADERS"

  val logger: Logger = LoggerFactory.getLogger(getClass)

  def persistedHeaders: Seq[String] =
    sys.env.get(envName).map(_.split(",").map(_.trim).toSeq).getOrElse(Seq.empty[String])

  def extract(persistedHeaders: Seq[String], metadata: Metadata): Seq[Header] = {
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

    capturedHeaders.toSeq
  }
}
