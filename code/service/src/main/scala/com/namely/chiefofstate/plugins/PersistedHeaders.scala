package com.namely.chiefofstate.plugins

import com.google.protobuf.ByteString
import com.namely.chiefofstate.plugin.{PluginBase, PluginFactory}
import com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers.{Header, Headers}
import com.namely.protobuf.chiefofstate.v1.service.ProcessCommandRequest
import io.grpc.Metadata

import scala.jdk.CollectionConverters._

private[this] class PersistedHeaders(persistedHeaders: Seq[String]) extends PluginBase {

  private val BINARY_SUFFIX: String = io.grpc.Metadata.BINARY_HEADER_SUFFIX

  override val pluginId: String = "persisted_headers.v1"

  override def run(processCommandRequest: ProcessCommandRequest,
                   metadata: Metadata
  ): Option[com.google.protobuf.any.Any] = {
    val headers: Seq[Header] = persistedHeaders.flatMap(header => {
      if (header.endsWith(BINARY_SUFFIX)) {
        val bytesKey: Metadata.Key[Array[Byte]] = Metadata.Key.of(header, Metadata.BINARY_BYTE_MARSHALLER)
        metadata
          .getAll[Array[Byte]](bytesKey)
          .asScala
          .map(b => Header().withKey(header).withBytesValue(ByteString.copyFrom(b)))
      } else {
        val stringKey: Metadata.Key[String] = Metadata.Key.of(header, Metadata.ASCII_STRING_MARSHALLER)
        metadata
          .getAll[String](stringKey)
          .asScala
          .map(s => Header().withKey(header).withStringValue(s))
      }
    })

    Some(com.google.protobuf.any.Any.pack(Headers().withHeaders(headers)))
  }
}

object PersistedHeaders extends PluginFactory {

  val envName: String = "COS_WRITE_PERSISTED_HEADERS"

  lazy val persistedHeaders: Seq[String] = sys.env
    .get(envName)
    .map(_.split(",").map(_.trim).toSeq)
    .getOrElse(Seq.empty[String])

  override def apply(): PluginBase = new PersistedHeaders(persistedHeaders)
}
