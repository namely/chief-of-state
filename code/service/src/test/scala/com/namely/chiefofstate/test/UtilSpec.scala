package com.namely.chiefofstate.test

import java.time.{Instant, ZoneId}

import com.google.protobuf.timestamp.Timestamp
import com.namely.chiefofstate.Util.{Instants, Timestamps}
import com.namely.chiefofstate.Util
import com.namely.chiefofstate.test.helper.BaseSpec
import com.namely.protobuf.chiefofstate.v1.tests.AccountOpened

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
      packageName shouldBe ("chief_of_state.v1.AccountOpened")
    }
  }
}
