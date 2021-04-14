/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration

import slick.dbio.{ DBIO, DBIOAction }

class VersionSpec extends BaseSpec {

  case class SomeVersion(versionNumber: Int) extends Version {
    override def snapshot(): DBIO[Unit] = DBIOAction.successful {}
    def upgrade(): DBIO[Unit] = DBIOAction.successful {}
  }

  "VersionOrdering" should {
    "order versions by version number" in {
      val versionOne = SomeVersion(1)
      val versionTwo = SomeVersion(2)

      val expectedCompare = versionOne.versionNumber.compare(versionTwo.versionNumber)

      Version.VersionOrdering.compare(versionOne, versionTwo) shouldBe expectedCompare
    }
  }

}
