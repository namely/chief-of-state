/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.plugin

import com.google.protobuf.ByteString
import com.namely.chiefofstate.helper.{BaseSpec, EnvironmentHelper}
import com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers.{Header, Headers}
import com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers.Header.Value.{BytesValue, StringValue}
import com.namely.protobuf.chiefofstate.v1.service.ProcessCommandRequest
import io.grpc.Metadata
import org.scalamock.scalatest.MockFactory

class PersistedHeadersSpec extends BaseSpec with MockFactory {

  override def beforeEach(): Unit = {
    super.beforeEach()
    EnvironmentHelper.clearEnv()
  }

  "PersistedHeaders" should {
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
      EnvironmentHelper.setEnv(PersistedHeaders.envName, "foo,bar-bin,not-a-key")

      val actual: Headers = PersistedHeaders
        .apply()
        .run(processCommandRequest, metadata)
        .get
        .unpack[com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers.Headers]

      val expected: Headers = Headers(Vector(fooHeader1, fooHeader2, barHeader))

      actual should be(expected)
    }

    "return an empty header" in {
      EnvironmentHelper.setEnv(PersistedHeaders.envName, "not-a-key,not-a-key-bin")

      val actual: Option[com.google.protobuf.any.Any] = PersistedHeaders
        .apply()
        .run(processCommandRequest, metadata)

      actual should be(None)
    }
  }
}
