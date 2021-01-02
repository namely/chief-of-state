/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import com.google.protobuf.timestamp.Timestamp
import com.namely.chiefofstate.Util.{Instants, Timestamps}
import com.namely.chiefofstate.helper.BaseSpec
import com.namely.protobuf.chiefofstate.v1.tests.AccountOpened
import io.grpc.{Metadata, Status, StatusException, StatusRuntimeException}

import java.time.{Instant, ZoneId}
import scala.util.Failure

class UtilSpec extends BaseSpec {
  "A protobuf Timestamp date" should {
    val ts = Timestamp().withSeconds(1582879956).withNanos(704545000)

    "be converted successfully to java Instant" in {
      val expected: Instant = Instant.ofEpochSecond(1582879956, 704545000)
      ts.toInstant.compareTo(expected) shouldBe 0
    }

    "be converted successfully to java Instant given the Timezone" in {
      ts.toInstant(ZoneId.of("America/Los_Angeles"))
        .compareTo(Instant.ofEpochSecond(1582879956, 704545000)) shouldBe 0

      ts.toInstant(ZoneId.of("GMT+01:00"))
        .compareTo(Instant.ofEpochSecond(1582879956, 704545000)) shouldBe 0
    }

    "be converted successfully to LocalDate given the Timezone" in {
      ts.toLocalDate(ZoneId.of("America/Los_Angeles")).toString === "2020-02-28"

      ts.toLocalDate(ZoneId.of("GMT+01:00")).toString === "2020-02-28"
    }
  }

  "An java Instant date" should {
    val instant = Instant.ofEpochSecond(1582879956, 704545000)
    "be convertes successfully to Protobuf Timestamp" in {
      instant.toTimestamp === Timestamp()
        .withSeconds(1582879956)
        .withNanos(704545000)
    }
  }

  "Extraction of proto package name" should {
    "be successful" in {
      val accountOpened: AccountOpened = AccountOpened()
      val packageName: String = Util.getProtoFullyQualifiedName(com.google.protobuf.any.Any.pack(accountOpened))
      packageName shouldBe "chief_of_state.v1.AccountOpened"
    }
  }
  "Transform gRPC metadata into RemoteCommand.Header" should {
    "successfully parse gRPC headers" in {
      val metadata: Metadata = new Metadata()
      val stringHeaderKey: Metadata.Key[String] = Metadata.Key.of("some-header", Metadata.ASCII_STRING_MARSHALLER)
      metadata.put(stringHeaderKey, "some header")
      val byteHeaderKey: Metadata.Key[Array[Byte]] = Metadata.Key.of("byte-header-bin", Metadata.BINARY_BYTE_MARSHALLER)
      metadata.put(byteHeaderKey, "".getBytes)

      val transformed = Util.transformMetadataToRemoteCommandHeader(metadata, Seq("some-header", "byte-header-bin"))
      transformed.size shouldBe 2
    }
  }

  ".makeFailedStatusPf" should {
    "invoke makeStatusException" in {
      val status: Status = Status.ABORTED.withDescription("abort!")
      val exc = new StatusRuntimeException(status)

      val actual = intercept[StatusException] {
        Failure(exc)
          .recoverWith(Util.makeFailedStatusPf)
          .get
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
      val expected = com.google.rpc.status.Status(
        code = io.grpc.Status.Code.INVALID_ARGUMENT.value(),
        message = "whoops"
      )
      actual shouldBe expected
    }

    "handles null status messages" in {
      val status = io.grpc.Status.INVALID_ARGUMENT
      val actual = Util.toRpcStatus(status)
      val expected = com.google.rpc.status.Status(
        code = io.grpc.Status.Code.INVALID_ARGUMENT.value(),
        message = ""
      )
      actual shouldBe expected
    }
  }

  ".getShardIndex" should {

    "be backwards compatible" in {
      // define pairs as they were in 0.5.1
      // these were pulled directly from the journal
      val expectedPairs = Map(
        0 -> 5,
        1 -> 4,
        2 -> 3,
        3 -> 2,
        4 -> 1,
        5 -> 0,
        6 -> 8,
        7 -> 7,
        8 -> 6,
        9 -> 5
      )

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
