/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration

import com.typesafe.config.{Config, ConfigFactory}

class MigratorSpec extends BaseSpec {

  // test helper to get a mock version
  def getMockVersion(versionNumber: Int): Version = {
    val mockVersion = mock[Version]

    (() => mockVersion.versionNumber)
      .expects()
      .returning(versionNumber)
      .atLeastOnce

    mockVersion
  }

  ".addVersion" should {
    "add to the versions queue in order" in {
      val cfg: Config = ConfigFactory.load()
      val migrator: Migrator = new Migrator(cfg)

      // add versions out of order
      migrator.addVersion(getMockVersion(2))
      migrator.addVersion(getMockVersion(1))
      migrator.addVersion(getMockVersion(3))

      val actual = migrator.getVersions()

      // assert they were actually added
      actual.size shouldBe 3

      // assert they are ascending
      actual.map(_.versionNumber) shouldBe Seq(1, 2, 3)
    }
  }

  ".getVersions" should {
    "filter versions" in {
      val cfg: Config = ConfigFactory.load()
      val migrator: Migrator = new Migrator(cfg)

      // add versions
      migrator.addVersion(getMockVersion(1))
      migrator.addVersion(getMockVersion(2))
      migrator.addVersion(getMockVersion(3))

      // assert they were actually added
      migrator.versions.size shouldBe 3

      // get versions 2 and up
      val actual = migrator.getVersions(2)

      actual.size shouldBe 2

      actual.map(_.versionNumber) shouldBe Seq(2, 3)
    }
  }

  ".beforeAll" should {
    "create the vresions table" in {
      // TODO
    }
  }

  ".run" should {
    "run latest snapshot" in {
      // TODO
    }
    "upgrade all available versions" in {
      // TODO
    }
    "no-op if no new versions to run" in {
      // TODO
    }
  }

  ".snapshotVersion" should {
    "run version snapshot and set version number" in {
      // TODO
    }
  }

  ".upgradeVersion" should {
    "run version upgrade and set version number" in {
      // TODO
      // confirm it runs before/after upgrade steps
    }
  }

  ".createMigrationsTable" should {
    "create the table if not exists" in {
      // TODO
    }
    "no-op if table exists" in {
      // TODO
    }
  }

  ".getCurrentVersionNumber" should {
    "return the latest versio" in {
      // TODO
    }
    "return None for no prior version" in {
      // TODO
    }
  }
  ".setCurrentVersionNumber" should {
    "write upgrade version to db" in {
      // TODO
    }
    "writes snapshot version to db" in {
      // TODO
    }
  }
}
