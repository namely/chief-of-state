package com.namely.chiefofstate.plugin

import com.namely.chiefofstate.test.helpers.TestSpec
import akka.grpc.scaladsl.{BytesEntry, Metadata, StringEntry}
import akka.util.ByteString
import org.mockito.Mockito
import com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers.Header.Value.{BytesValue, StringValue}
import com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers.{Header, Headers}

class PersistedHeadersSpec extends TestSpec {
  "Persistedheaders" should {
    val foo: String = "foo"
    val fooByteString: ByteString = ByteString.apply(foo)
    val stringValue: StringValue = StringValue.apply(foo)
    val stringEntry: StringEntry = StringEntry.apply(foo)
    val fooHeader: Header = Header.apply(foo, stringValue)

    val bar: String = "bar"
    val barByteString: ByteString = ByteString.apply(bar)
    val bytesValue: BytesValue = BytesValue.apply(com.google.protobuf.ByteString.copyFrom(bar.getBytes()))
    val bytesEntry: BytesEntry = BytesEntry.apply(barByteString)
    val barHeader: Header = Header.apply(bar, bytesValue)

    val metadata: Metadata = Mockito.mock(classOf[Metadata])
    Mockito.when(metadata.getText(foo)).thenReturn(Some(foo))
    Mockito.when(metadata.getBinary(foo)).thenReturn(Some(fooByteString))
    Mockito.when(metadata.getText(bar)).thenReturn(Some(bar))
    Mockito.when(metadata.getBinary(bar)).thenReturn(Some(barByteString))
    Mockito.when(metadata.asMap).thenReturn(Map(
      foo -> List(stringEntry),
      bar -> List(bytesEntry)
    ))
    Mockito.when(metadata.asList).thenReturn(List(
      (foo, stringEntry),
      (bar, bytesEntry)
    ))


    "return the a string and byte header" in {
      val actual = PersistHeaders
        .makeMeta(metadata)
        .get
        .unpack[com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers.Headers]

      val expected: Headers = Headers.apply(Vector(fooHeader, barHeader))

      actual should be (expected)
    }
  }
}
