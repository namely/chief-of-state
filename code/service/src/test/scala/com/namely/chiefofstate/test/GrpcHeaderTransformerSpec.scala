package com.namely.chiefofstate.test

import com.namely.chiefofstate.test.helper.BaseSpec
import com.namely.chiefofstate.GrpcHeaderTransformer
import com.namely.protobuf.chiefofstate.v1.internal.GrpcHeader
import io.grpc.Metadata

class GrpcHeaderTransformerSpec extends BaseSpec {
  "GrpcHeaderTransformer" should {
    "successfully parse gRPC headers" in {
      val metadata: Metadata = new Metadata()
      val stringHeaderKey: Metadata.Key[String] = Metadata.Key.of("some-header", Metadata.ASCII_STRING_MARSHALLER)
      metadata.put(stringHeaderKey, "some header")
      val byteHeaderKey: Metadata.Key[Array[Byte]] = Metadata.Key.of("byte-header-bin", Metadata.BINARY_BYTE_MARSHALLER)
      metadata.put(byteHeaderKey, "".getBytes)

      val transformed: GrpcHeader = GrpcHeaderTransformer.transform(metadata)
      transformed.headers.size shouldBe (2)
    }
  }
}
