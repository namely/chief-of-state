/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import com.google.protobuf.timestamp.Timestamp
import com.google.rpc.error_details.BadRequest
import com.namely.chiefofstate.Util.{ Instants, Timestamps }
import com.namely.chiefofstate.helper.BaseSpec
import com.namely.protobuf.chiefofstate.v1.tests.AccountOpened
import io.grpc.{ Metadata, Status, StatusException, StatusRuntimeException }
import io.grpc.protobuf.StatusProto
import com.namely.protobuf.chiefofstate.v1.common.Header
import com.namely.protobuf.chiefofstate.v1.common.Header.Value.{ BytesValue, StringValue }

import java.time.{ Instant, ZoneId }
import scala.util.Failure
import com.google.protobuf.ByteString

class UtilSpec extends BaseSpec {
  "A protobuf Timestamp date" should {
    val ts = Timestamp().withSeconds(1582879956).withNanos(704545000)

    "be converted successfully to java Instant" in {
      val expected: Instant = Instant.ofEpochSecond(1582879956, 704545000)
      ts.toInstant.compareTo(expected) shouldBe 0
    }

    "be converted successfully to java Instant given the Timezone" in {
      ts.toInstant(ZoneId.of("America/Los_Angeles")).compareTo(Instant.ofEpochSecond(1582879956, 704545000)) shouldBe 0

      ts.toInstant(ZoneId.of("GMT+01:00")).compareTo(Instant.ofEpochSecond(1582879956, 704545000)) shouldBe 0
    }

    "be converted successfully to LocalDate given the Timezone" in {
      ts.toLocalDate(ZoneId.of("America/Los_Angeles")).toString === "2020-02-28"

      ts.toLocalDate(ZoneId.of("GMT+01:00")).toString === "2020-02-28"
    }
  }

  "An java Instant date" should {
    val instant = Instant.ofEpochSecond(1582879956, 704545000)
    "be convertes successfully to Protobuf Timestamp" in {
      instant.toTimestamp === Timestamp().withSeconds(1582879956).withNanos(704545000)
    }
  }

  "Extraction of proto package name" should {
    "be successful" in {
      val accountOpened: AccountOpened = AccountOpened()
      val packageName: String = Util.getProtoFullyQualifiedName(com.google.protobuf.any.Any.pack(accountOpened))
      packageName shouldBe "chief_of_state.v1.AccountOpened"
    }
  }
  ".extractHeaders" should {
    "successfully parse gRPC headers" in {
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

      val desiredHeaders: Seq[String] = Seq("foo", "bar-bin", "not-a-key")
      val actual: Seq[Header] = Util.extractHeaders(metadata, desiredHeaders)
      val expected: Seq[Header] = Seq(fooHeader1, fooHeader2, barHeader)
      actual should contain theSameElementsAs expected
    }
  }

  ".makeFailedStatusPf" should {
    "invoke makeStatusException" in {
      val status: Status = Status.ABORTED.withDescription("abort!")
      val exc = new StatusRuntimeException(status)

      val actual = intercept[StatusException] {
        Failure(exc).recoverWith(Util.makeFailedStatusPf).get
      }

      actual.getStatus shouldBe status
    }
  }

  ".makeStatusException" should {
    "pass through status exceptions" in {
      // define an illegal arg exception
      val status: Status = Status.ABORTED.withDescription("abort!")
      val exc = new StatusException(status)
      // convert to a status exception of INVALID ARGUMENT
      val actual: StatusException = Util.makeStatusException(exc)
      actual shouldBe exc
    }
    "transform status runtime exceptions" in {
      // define an illegal arg exception
      val status: Status = Status.ABORTED.withDescription("abort!")
      val exc = new StatusRuntimeException(status)
      // convert to a status exception of INVALID ARGUMENT
      val actual: StatusException = Util.makeStatusException(exc)

      actual.getStatus shouldBe status
    }
    "transform illegal argument exceptions" in {
      // define an illegal arg exception
      val exc = new IllegalArgumentException("some illegal thing")
      // convert to a status exception of INVALID ARGUMENT
      val actual: StatusException = Util.makeStatusException(exc)

      actual.getStatus.getCode shouldBe (io.grpc.Status.Code.INVALID_ARGUMENT)
      actual.getStatus.getDescription shouldBe "some illegal thing"
    }
    "convert general throwables to INTERNAL status" in {
      val exc = new Exception("boom")
      val actual: StatusException = Util.makeStatusException(exc)

      actual.getStatus.getCode shouldBe (io.grpc.Status.Code.INTERNAL)
      actual.getStatus.getDescription shouldBe "boom"
    }
  }

  ".toRpcStatus" should {
    "convert statuses to the google rpc class" in {
      val status = io.grpc.Status.INVALID_ARGUMENT.withDescription("whoops")
      val actual = Util.toRpcStatus(status)
      val expected =
        com.google.rpc.status.Status(code = io.grpc.Status.Code.INVALID_ARGUMENT.value(), message = "whoops")
      actual shouldBe expected
    }

    "handles null status messages" in {
      val status = io.grpc.Status.INVALID_ARGUMENT
      val actual = Util.toRpcStatus(status)
      val expected = com.google.rpc.status.Status(code = io.grpc.Status.Code.INVALID_ARGUMENT.value(), message = "")
      actual shouldBe expected
    }

    "convert status with trailers to the google rpc class preserving details" in {
      // define a field violation
      val errField = BadRequest.FieldViolation().withField("some_field").withDescription("oh no")

      // create the bad request detail
      val errDetail: BadRequest = BadRequest().addFieldViolations(errField)

      // create an error status with this detail
      val expectedStatus: com.google.rpc.status.Status =
        com.google.rpc.status
          .Status()
          .withCode(com.google.rpc.code.Code.INVALID_ARGUMENT.value)
          .withMessage("some error message")
          .addDetails(com.google.protobuf.any.Any.pack(errDetail))

      // construct a status exception
      val statusException: StatusException = {
        // convert to java version of the status proto
        val javaErrStatus = com.google.rpc.Status.parseFrom(expectedStatus.toByteArray)
        // use provided helper to convert to a status exception,
        // which conveniently converts the error details to
        // trailers for us
        StatusProto.toStatusException(javaErrStatus)
      }

      // run our method and pass in the status and trailers
      val actualStatus: com.google.rpc.status.Status =
        Util.toRpcStatus(statusException.getStatus(), statusException.getTrailers())

      // assert that error status with details was not lost
      actualStatus shouldBe expectedStatus
    }
  }

  ".getShardIndex" should {

    "be backwards compatible" in {
      // define pairs as they were in 0.5.1
      // these were pulled directly from the journal
      val expectedPairs = Map(0 -> 5, 1 -> 4, 2 -> 3, 3 -> 2, 4 -> 1, 5 -> 0, 6 -> 8, 7 -> 7, 8 -> 6, 9 -> 5)

      val actual = expectedPairs.toSeq
        .sortBy(_._1)
        .map(_._1)
        .map(input => {
          val id: String = s"test-id-$input"
          Util.getShardIndex(id, 9)
        })

      val expected = expectedPairs.toSeq.sortBy(_._1).map(_._2)

      actual shouldBe expected
    }
  }
}
