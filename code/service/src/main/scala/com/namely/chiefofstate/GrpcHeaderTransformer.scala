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
    GrpcHeader().withHeaders(
      headers
        .keys()
        .asScala
        .toSeq
        .flatMap(k => {
          if (k.endsWith(BINARY_SUFFIX)) {
            headers
              .getAll(Metadata.Key.of(k, Metadata.BINARY_BYTE_MARSHALLER))
              .asScala
              .map(v => {
                Header().withKey(k).withBytesValue(ByteString.copyFrom(v))
              })
          } else {
            headers
              .getAll(Metadata.Key.of(k, Metadata.ASCII_STRING_MARSHALLER))
              .asScala
              .map(v => Header().withKey(k).withStringValue(v))
          }
        })
    )
  }
}
