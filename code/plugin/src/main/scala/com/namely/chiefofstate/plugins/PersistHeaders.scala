//package com.namely.chiefofstate.plugins
//
//// TODO: Use a generalized Metadata for GRPC
//import com.google.protobuf.ByteString
//import com.google.protobuf.any.Any
//import com.typesafe.config.Config
//import com.namely.chiefofstate.config.SendCommandSettings
//import com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers.{Header, Headers}
//import com.namely.protobuf.chiefofstate.v1.service.ProcessCommandRequest
//
//object PersistHeaders extends PluginBase {
//  override val pluginId: String = "persisted_headers.v1"
//
//  val sendCommandSettings: SendCommandSettings = SendCommandSettings(ConfigFactory.load())
//
//  override protected def makeMeta(any: Any): Option[com.google.protobuf.any.Any] = {
//    val persistedHeaders: Seq[Header] = any.asInstanceOf[Metadata].asList
//      .filter({ case (k, _) => sendCommandSettings.propagatedHeaders.contains(k) })
//      .map({
//        case (k, StringEntry(value)) =>
//          com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers
//            .Header()
//            .withKey(k)
//            .withStringValue(value)
//
//        case (k, BytesEntry(value)) =>
//          com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers
//            .Header()
//            .withKey(k)
//            .withBytesValue(ByteString.copyFrom(value.toArray))
//      })
//
//    Some(com.google.protobuf.any.Any.pack(Headers().withHeaders(persistedHeaders)))
//  }
//}
