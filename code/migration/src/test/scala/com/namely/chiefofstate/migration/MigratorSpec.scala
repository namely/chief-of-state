/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration

import com.typesafe.config.{Config, ConfigFactory}
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import slick.basic.DatabaseConfig
import slick.jdbc.{JdbcProfile, PostgresProfile}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import slick.jdbc.PostgresProfile.api._
import scala.annotation.migration

class MigratorSpec extends BaseSpec {

  var pg: EmbeddedPostgres = _

  val cosSchema: String = "cos"

  override def beforeAll() = {
    super.beforeAll()
    val builder: EmbeddedPostgres.Builder = EmbeddedPostgres.builder()
    builder.setPort(25432)
    pg = builder.start()
  }

  override def beforeEach() = {
    super.beforeEach()

    val cfg: Config = getDbConfig("public")
    val dbConfig = DatabaseConfig.forConfig[JdbcProfile]("jdbc-default", cfg)

    val stmt = sqlu"drop schema if exists #$cosSchema cascade".andThen(sqlu"create schema #$cosSchema")

    val future = dbConfig.db.run(stmt)

    Await.result(future, Duration.Inf)
  }

  override protected def afterAll() = {
    super.afterAll()
    pg.close()
  }

  def getDbConfig(schemaName: String): Config = {

    val cfgString: String = s"""
      jdbc-default {
        profile = "slick.jdbc.PostgresProfile$$"
        db {
          connectionPool = disabled
          driver = "org.postgresql.Driver"
          user = "postgres"
          password = "changeme"
          serverName = "locahost"
          portNumber = 25432
          databaseName = "postgres"
          schemaName = "$schemaName"
          url = "jdbc:postgresql://localhost:25432/postgres?currentSchema=$schemaName"
        }
      }
    """

    ConfigFactory.parseString(cfgString)
  }

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
    "create the versions table" in {
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
      val config: Config = getDbConfig(cosSchema)

      val dbConfig: DatabaseConfig[JdbcProfile] =
        DatabaseConfig.forConfig[JdbcProfile]("jdbc-default", config)

      val actual = Migrator.createMigrationsTable(dbConfig)

      actual.isSuccess shouldBe true

      DbUtil.tableExists(dbConfig, Migrator.COS_MIGRATIONS_TABLE) shouldBe true
    }
    "no-op if table exists" in {
      val config: Config = getDbConfig(cosSchema)

      val dbConfig: DatabaseConfig[JdbcProfile] =
        DatabaseConfig.forConfig[JdbcProfile]("jdbc-default", config)

      // assert doesn't exist
      DbUtil.tableExists(dbConfig, Migrator.COS_MIGRATIONS_TABLE) shouldBe false
      // create it
      Migrator.createMigrationsTable(dbConfig).isSuccess shouldBe true
      DbUtil.tableExists(dbConfig, Migrator.COS_MIGRATIONS_TABLE) shouldBe true
      // assert it no-ops second time
      Migrator.createMigrationsTable(dbConfig).isSuccess shouldBe true
      DbUtil.tableExists(dbConfig, Migrator.COS_MIGRATIONS_TABLE) shouldBe true

    }
  }

  ".getCurrentVersionNumber" should {
    "return the latest version" in {
      // TODO
    }
    "return None for no prior version" in {
      // TODO
    }
  }
  ".setCurrentVersionNumber" should {
    "write versions to db" in {
      val config: Config = getDbConfig(cosSchema)

      val dbConfig: DatabaseConfig[JdbcProfile] =
        DatabaseConfig.forConfig[JdbcProfile]("jdbc-default", config)

      // create the migrations table
      Migrator.createMigrationsTable(dbConfig).isSuccess shouldBe true

      val stmt = Migrator
        .setCurrentVersionNumber(dbConfig, 2, true)
        .andThen(Migrator.setCurrentVersionNumber(dbConfig, 3, false))

      val future = dbConfig.db.run(stmt)

      Await.ready(future, Duration.Inf)

      val readFuture = dbConfig.db.run(sql"""
      select version_number, is_snapshot
      from #${Migrator.COS_MIGRATIONS_TABLE}
      """.as[(Int, Boolean)])

      val actual = Await.result(readFuture, Duration.Inf)

      actual.size shouldBe 2

      actual.toSeq should contain theSameElementsAs Seq((2, true), (3, false))
    }
  }
}
