package com.namely.chiefofstate.plugin.utils

import io.grpc
import akka.grpc.scaladsl
import akka.util.ByteString
import org.scalamock.scalatest.MockFactory
import org.scalatest.TestSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.jdk.CollectionConverters._

class MetadataUtilSpec
  extends AnyWordSpecLike
    with Matchers
    with TestSuite
    with MockFactory {

  "MetadataUtil" should {
    val s: String = "foo"
    val sKey: grpc.Metadata.Key[String] = grpc.Metadata.Key.of(s, grpc.Metadata.ASCII_STRING_MARSHALLER)

    val b: Array[Byte] = "bar".getBytes()
    val bKeyName: String = "bar-bin"
    val bKey: grpc.Metadata.Key[Array[Byte]] = grpc.Metadata.Key.of(bKeyName, grpc.Metadata.BINARY_BYTE_MARSHALLER)

    val akkaMetadataBuilder: scaladsl.MetadataBuilder = new scaladsl.MetadataBuilder()
    akkaMetadataBuilder.addText(s, s)
    akkaMetadataBuilder.addBinary(bKeyName, ByteString(b))
    val akkaMetadata: scaladsl.Metadata = akkaMetadataBuilder.build()

    val ioMetadata: grpc.Metadata = new grpc.Metadata()
    ioMetadata.put(sKey, s)
    ioMetadata.put(bKey, b)

    "return an Akka gRPC instance" in {
      MetadataUtil.makeMeta(ioMetadata).asList should contain theSameElementsAs akkaMetadata.asList
    }
    "return an io.gRPC instance" in {
      val actual: grpc.Metadata = MetadataUtil.makeMeta(akkaMetadata)
      actual.keys() should be (ioMetadata.keys())
      actual.getAll(sKey).asScala should contain theSameElementsAs ioMetadata.getAll(sKey).asScala
      actual.getAll(bKey).asScala should contain theSameElementsAs ioMetadata.getAll(bKey).asScala
    }
  }
}
