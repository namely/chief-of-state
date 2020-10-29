package com.namely.chiefofstate.plugin

import com.google.protobuf.ByteString
import com.namely.chiefofstate.config.SendCommandSettings
import com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers.{Header, Headers}
import com.namely.protobuf.chiefofstate.v1.service.ProcessCommandRequest
import com.typesafe.config.ConfigFactory
import io.grpc.Metadata
import scala.jdk.CollectionConverters._

object PersistHeaders extends PluginBase {
  override val pluginId: String = "persisted_headers.v1"

  val getSendCommandSettings: SendCommandSettings = SendCommandSettings(ConfigFactory.load())

  override def makeAny(processCommandRequest: ProcessCommandRequest, metadata: Metadata): Option[com.google.protobuf.any.Any] = {
    val headers: Seq[Header] = getSendCommandSettings.propagatedHeaders.flatMap(header => {
      if(header.endsWith("-bin")) {
        val bytesKey: Metadata.Key[Array[Byte]] = Metadata.Key.of(header, Metadata.BINARY_BYTE_MARSHALLER)
        metadata.getAll[Array[Byte]](bytesKey).asScala
          .map(b => Header().withKey(header).withBytesValue(ByteString.copyFrom(b)))
      } else {
        val stringKey: Metadata.Key[String] = Metadata.Key.of(header, Metadata.ASCII_STRING_MARSHALLER)
        metadata.getAll[String](stringKey).asScala
          .map(s => Header().withKey(header).withStringValue(s))
      }
    }).toSeq

    Some(com.google.protobuf.any.Any.pack(Headers().withHeaders(headers)))
  }
}
