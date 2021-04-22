/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import com.google.protobuf.ByteString
import com.namely.chiefofstate.helper.{ BaseSpec, EnvironmentHelper }
import com.namely.protobuf.chiefofstate.v1.common.Header
import com.namely.protobuf.chiefofstate.v1.common.Header.Value.{ BytesValue, StringValue }
import com.namely.protobuf.chiefofstate.v1.service.ProcessCommandRequest
import io.grpc.Metadata

class PersistedHeadersSpec extends BaseSpec {

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

  val metadata: Metadata = new Metadata()
  metadata.put(fooKey, foo1)
  metadata.put(fooKey, foo2)
  metadata.put(barKey, bar)
  metadata.put(bazKey, baz)

  ".extract" should {
    "return the a string and byte header" in {
      val desiredHeaders: Seq[String] = Seq("foo", "bar-bin", "not-a-key")
      val actual: Seq[Header] = PersistedHeaders.extract(desiredHeaders, metadata)
      val expected: Seq[Header] = Seq(fooHeader1, fooHeader2, barHeader)
      actual should contain theSameElementsAs expected
    }

    "return an empty sequence" in {
      val desiredHeaders: Seq[String] = Seq("not-a-key", "not-a-key-bin")
      val actual: Seq[Header] = PersistedHeaders.extract(desiredHeaders, metadata)
      actual.isEmpty shouldBe true
    }
  }
}
