package com.namely.chiefofstate.plugin

import org.scalamock.scalatest.MockFactory
import com.google.protobuf.ByteString
import io.grpc.Metadata
import com.namely.chiefofstate.test.helpers.TestSpec
import com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers.Header.Value.{BytesValue, StringValue}
import com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers.{Header, Headers}
import com.namely.protobuf.chiefofstate.v1.service.ProcessCommandRequest

class PersistedHeadersSpec extends TestSpec with MockFactory {
  "Persistedheaders" should {
    val fooKeyName: String = "foo"
    val fooKey: Metadata.Key[String] = Metadata.Key.of(fooKeyName, Metadata.ASCII_STRING_MARSHALLER)

    val foo1: String = "foo"
    val fooStringValue1: StringValue = StringValue(foo1)
    val fooHeader1: Header = Header(fooKeyName, fooStringValue1)

    val foo2: String = "someOtherFoo"
    val fooStringValue2: StringValue = StringValue(foo2)
    val fooHeader2: Header = Header(fooKeyName, fooStringValue2)

    val barKeyName: String = "bar-bin"
    val bar: Array[Byte] = "bar".getBytes
    val barKey: Metadata.Key[Array[Byte]] = Metadata.Key.of(barKeyName, Metadata.BINARY_BYTE_MARSHALLER)
    val barBytesValue: BytesValue = BytesValue(ByteString.copyFrom(bar))
    val barHeader: Header = Header(barKeyName, barBytesValue)

    val baz: String = "baz"
    val bazKey: Metadata.Key[String] = Metadata.Key.of(baz, Metadata.ASCII_STRING_MARSHALLER)

    val processCommandRequest: ProcessCommandRequest = ProcessCommandRequest.defaultInstance

    val metadata: Metadata = new Metadata()
    metadata.put(fooKey, foo1)
    metadata.put(fooKey, foo2)
    metadata.put(barKey, bar)
    metadata.put(bazKey, baz)

    "return the a string and byte header" in {
      val actual: Headers = PersistHeaders
        .apply()
        .run(processCommandRequest, metadata)
        .get
        .unpack[com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers.Headers]

      val expected: Headers = Headers(Vector(fooHeader1, fooHeader2, barHeader))

      actual should be (expected)
    }
  }
}