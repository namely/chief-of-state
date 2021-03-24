/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration

class PackageSpec extends BaseSpec {

  ".isUpper" should {
    "be true" in {
      "AKKA_PROJECTION_OFFSET_STORE".isUpper shouldBe true
      "AKKA PROJECTION OFFSET STORE".isUpper shouldBe true
    }

    "be false with mixed cases" in {
      val tableName: String = "AKKA_read"
      tableName.isUpper shouldBe false
    }

    "be false lowercase" in {
      val tableName: String = "read_side_offset"
      tableName.isUpper shouldBe false
    }
  }

  ".quote" should {
    "work as expected" in {
      val result: String = "AKKA_PROJECTION_OFFSET_STORE".quote
      result shouldBe s"""\"AKKA_PROJECTION_OFFSET_STORE\""""
    }
  }
}
