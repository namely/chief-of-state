/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.helper

import io.grpc._

import java.io.Closeable
import java.util.concurrent.TimeUnit
import scala.collection.mutable

object GrpcHelpers {

  def getHeaders(headers: (String, String)*): Metadata = {
    val metadata: Metadata = new Metadata()
    headers.foreach({
      case (k, v) =>
        metadata.put(
          Metadata.Key.of(k, Metadata.ASCII_STRING_MARSHALLER),
          v
        )
    })
    metadata
  }

  def getStringHeader(headers: Metadata, key: String): String = {
    headers.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER))
  }

  class Closeables() {
    val resources: mutable.ListBuffer[Closeable] = mutable.ListBuffer.empty[Closeable]

    def register(server: io.grpc.Server): io.grpc.Server = {
      val closeable: Closeable = () => {
        server.shutdownNow()
        server.awaitTermination(10000, TimeUnit.MILLISECONDS)
      }

      register(closeable)

      server
    }

    def register(channel: ManagedChannel): ManagedChannel = {
      val closeable: Closeable = () => {
        channel.shutdownNow()
        channel.awaitTermination(10000, TimeUnit.MILLISECONDS)
      }

      register(closeable)

      channel
    }

    def register[T <: Closeable](closeable: T): T = {
      resources.append(closeable)
      closeable
    }

    def closeAll(): Unit = {
      resources.foreach(_.close())
      resources.clear
    }
  }
}
