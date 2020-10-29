package com.namely.chiefofstate.plugin.utils

import io.grpc
import akka.grpc.scaladsl
import akka.util.ByteString

import scala.jdk.CollectionConverters._

object MetadataUtil {

  /**
   * Constructs an io.gRPC Metadata instance from an Akka gRPC Metadata instance
   *
   * @param akkaMetadata Akka gRPC Metadata
   * @return io.gRPC Metadata
   */
  def makeMeta(akkaMetadata: scaladsl.Metadata): grpc.Metadata = {
    val grpcMetadata: grpc.Metadata = new grpc.Metadata()

    akkaMetadata.asList.foreach { case (k, v) =>
      v match {
        case x: scaladsl.BytesEntry =>
          val key: grpc.Metadata.Key[Array[Byte]] = grpc.Metadata.Key.of(k, grpc.Metadata.BINARY_BYTE_MARSHALLER)
          grpcMetadata.put(key, x.getValue().toArray)
        case x: scaladsl.StringEntry =>
          val key: grpc.Metadata.Key[String] = grpc.Metadata.Key.of(k, grpc.Metadata.ASCII_STRING_MARSHALLER)
          grpcMetadata.put(key, x.getValue())
      }
    }

    grpcMetadata
  }

  /**
   * Constructs an Akka gRPC Metadata instance from an io.gRPC Metadata instance
   *
   * @param grpcMetadata io.gRPC Metadata
   * @return Akka gRPC Metadata
   */
  def makeMeta(grpcMetadata: grpc.Metadata): scaladsl.Metadata = {
    val keys: Set[String] = grpcMetadata.keys().asScala.toSet

    val akkaMetadataBuilder: scaladsl.MetadataBuilder = new scaladsl.MetadataBuilder()

    keys.foreach(k => {
      if(k.endsWith("-bin")) {
        val grpcKey: grpc.Metadata.Key[Array[Byte]] = grpc.Metadata.Key.of(k, grpc.Metadata.BINARY_BYTE_MARSHALLER)
        val ite: Iterable[Array[Byte]] = grpcMetadata.getAll(grpcKey).asScala

        ite.foreach(v => akkaMetadataBuilder.addBinary(k, ByteString(v)))
      } else {
        val grpcKey: grpc.Metadata.Key[String] = grpc.Metadata.Key.of(k, grpc.Metadata.ASCII_STRING_MARSHALLER)
        val ite: Iterable[String] = grpcMetadata.getAll(grpcKey).asScala

        ite.foreach(v => akkaMetadataBuilder.addText(k, v))
      }
    })

    akkaMetadataBuilder.build()
  }
}
