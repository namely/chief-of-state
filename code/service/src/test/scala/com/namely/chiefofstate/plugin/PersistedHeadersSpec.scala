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
    val fooStringValue: StringValue = StringValue.apply(foo)
    val fooStringEntry: StringEntry = StringEntry.apply(foo)
    val fooHeader: Header = Header.apply(foo, fooStringValue)

    val bar: String = "bar"
    val barByteString: ByteString = ByteString.apply(bar)
    val barBytesValue: BytesValue = BytesValue.apply(com.google.protobuf.ByteString.copyFrom(bar.getBytes()))
    val barBytesEntry: BytesEntry = BytesEntry.apply(barByteString)
    val barHeader: Header = Header.apply(bar, barBytesValue)

    val baz: String = "baz"
    val bazStringValue: StringValue = StringValue.apply(baz)
    val bazHeader: Header = Header.apply(baz, bazStringValue)
    
    val metadata: Metadata = Mockito.mock(classOf[Metadata])
    Mockito.when(metadata.getText(foo)).thenReturn(Some(foo))
    Mockito.when(metadata.getBinary(foo)).thenReturn(Some(fooByteString))
    Mockito.when(metadata.getText(bar)).thenReturn(Some(bar))
    Mockito.when(metadata.getBinary(bar)).thenReturn(Some(barByteString))
    Mockito.when(metadata.asMap).thenReturn(Map(
      foo -> List(fooStringEntry),
      bar -> List(barBytesEntry)
    ))
    Mockito.when(metadata.asList).thenReturn(List(
      (foo, fooStringEntry),
      (bar, barBytesEntry)
    ))


    "return the a string and byte header" in {
      // TODO: Have the configs only return foo and bar

      val actual: Headers = PersistHeaders
        .makeMeta(metadata)
        .get
        .unpack[com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers.Headers]

      val expected: Headers = Headers.apply(Vector(fooHeader, barHeader, bazHeader))

      actual should be (expected)
    }
  }
}
