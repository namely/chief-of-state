package com.namely.chiefofstate.plugin

// TODO: Use a generalized Metadata for GRPC
import akka.grpc.scaladsl.{BytesEntry, Metadata, StringEntry}
import com.google.protobuf.ByteString
import com.namely.chiefofstate.config.SendCommandSettings
import com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers.{Header, Headers}
import com.typesafe.config.ConfigFactory

object PersistHeaders extends PluginBase {
  override val pluginId: String = "persisted_headers.v1"

  val getSendCommandSettings: SendCommandSettings = SendCommandSettings(ConfigFactory.load())

  override def makeAny(any: Any): Option[com.google.protobuf.any.Any] = {
    val persistedHeaders: Seq[Header] = any.asInstanceOf[Metadata].asList
      .filter({ case (k, _) => getSendCommandSettings.propagatedHeaders.contains(k) })
      .map({
        case (k, StringEntry(value)) =>
          com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers
            .Header()
            .withKey(k)
            .withStringValue(value)
        case (k, BytesEntry(value)) =>
          com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers
            .Header()
            .withKey(k)
            .withBytesValue(ByteString.copyFrom(value.toArray))
      })

    Some(com.google.protobuf.any.Any.pack(Headers().withHeaders(persistedHeaders)))
  }
}
