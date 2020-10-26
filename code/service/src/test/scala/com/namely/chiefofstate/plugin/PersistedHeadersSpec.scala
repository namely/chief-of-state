package com.namely.chiefofstate.plugin

import com.namely.chiefofstate.test.helpers.TestSpec
import akka.grpc.scaladsl.{BytesEntry, Metadata, StringEntry}
import akka.util.ByteString
import com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers.Header.Value.{BytesValue, StringValue}
import com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers.{Header, Headers}
import org.scalamock.scalatest.MockFactory

class PersistedHeadersSpec extends TestSpec with MockFactory {
  "Persistedheaders" should {
    val foo: String = "foo"
    val fooStringValue: StringValue = StringValue.apply(foo)
    val fooStringEntry: StringEntry = StringEntry.apply(foo)
    val fooHeader: Header = Header.apply(foo, fooStringValue)

    val bar: String = "bar"
    val barByteString: ByteString = ByteString.apply(bar)
    val barBytesValue: BytesValue = BytesValue.apply(com.google.protobuf.ByteString.copyFrom(bar.getBytes()))
    val barBytesEntry: BytesEntry = BytesEntry.apply(barByteString)
    val barHeader: Header = Header.apply(bar, barBytesValue)

    val baz: String = "baz"
    val bazStringEntry: StringEntry = StringEntry.apply(baz)

    val metadata: Metadata = mock[Metadata]
    (metadata.asList _).expects().returning(List(
      (foo, fooStringEntry),
      (bar, barBytesEntry),
      (baz, bazStringEntry)
    ))

    "return the a string and byte header" in {
      val actual: Headers = PersistHeaders
        .makeAny(metadata)
        .get
        .unpack[com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers.Headers]

      val expected: Headers = Headers.apply(Vector(fooHeader, barHeader))

      println(actual)
      println(expected)

      actual should be (expected)
    }
  }
}
