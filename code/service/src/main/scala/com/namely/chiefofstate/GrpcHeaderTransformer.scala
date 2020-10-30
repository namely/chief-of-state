package com.namely.chiefofstate

import com.google.protobuf.ByteString
import com.namely.protobuf.chiefofstate.v1.internal.{GrpcHeader, Header}
import io.grpc.Metadata

import scala.jdk.CollectionConverters.{CollectionHasAsScala, IterableHasAsScala}

object GrpcHeaderTransformer {
  private val BINARY_SUFFIX: String = io.grpc.Metadata.BINARY_HEADER_SUFFIX

  /**
   * transform a gRPC request headers into a RequestHeader
   * @param headers the gRPC request headers
   * @return GrpcHeader
   */
  def transform(headers: Metadata): GrpcHeader = {

    val rpcHeaders: Seq[Header] = Seq.empty
    val keys: Seq[String] = headers.keys().asScala.toSeq

    keys.foreach(k => {
      if (k.endsWith(BINARY_SUFFIX)) {
        val key: Metadata.Key[Array[Byte]] = Metadata.Key.of(k, Metadata.BINARY_BYTE_MARSHALLER)
        val values: Iterable[Array[Byte]] = headers.getAll(key).asScala
        values.foreach(v => rpcHeaders :+ Header().withKey(k).withBytesValue(ByteString.copyFrom(v)))
      } else {
        val key: Metadata.Key[String] = Metadata.Key.of(k, Metadata.ASCII_STRING_MARSHALLER)
        val values: Iterable[String] = headers.getAll(key).asScala
        values.foreach(v => rpcHeaders :+ Header().withKey(k).withStringValue(v))
      }
    })

    GrpcHeader().withHeaders(rpcHeaders)
  }
}
