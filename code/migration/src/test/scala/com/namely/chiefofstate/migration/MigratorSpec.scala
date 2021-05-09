/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import com.dimafeng.testcontainers.{ ForAllTestContainer, PostgreSQLContainer }
import com.namely.chiefofstate.migration.helper.TestConfig
import com.namely.chiefofstate.migration.helper.TestConfig.dbConfigFromUrl
import org.testcontainers.utility.DockerImageName
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

import java.sql.DriverManager
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Success

class MigratorSpec extends BaseSpec with ForAllTestContainer {

  val cosSchema: String = "cos"

  override val container: PostgreSQLContainer = PostgreSQLContainer
    .Def(dockerImageName = DockerImageName.parse("postgres:11"), urlParams = Map("currentSchema" -> cosSchema))
    .createContainer()

  val testKit: ActorTestKit = ActorTestKit()

  def recreateSchema(): Unit = {
    // load the driver
    Class.forName("org.postgresql.Driver")

    val connection = DriverManager.getConnection(container.jdbcUrl, container.username, container.password)

    val statement = connection.createStatement()
    statement.addBatch(s"drop schema if exists $cosSchema cascade")
    statement.addBatch(s"create schema $cosSchema")
    statement.executeBatch()
  }

  override def beforeEach() = {
    super.beforeEach()
    recreateSchema()
    clearEnv()
  }

  override protected def afterAll() = {
    super.afterAll()
    testKit.shutdownTestKit()
    clearEnv()
  }

  def setEnv(key: String, value: String): Unit = {
    val field = System.getenv().getClass.getDeclaredField("m")
    field.setAccessible(true)
    val map: java.util.Map[java.lang.String, java.lang.String] =
      field.get(System.getenv()).asInstanceOf[java.util.Map[java.lang.String, java.lang.String]]
    map.put(key, value)
  }

  def clearEnv(): Unit = {
    val field = System.getenv().getClass.getDeclaredField("m")
    field.setAccessible(true)
    val map: java.util.Map[java.lang.String, java.lang.String] =
      field.get(System.getenv()).asInstanceOf[java.util.Map[java.lang.String, java.lang.String]]
    map.clear()
  }

  // test helper to get a mock version
  def getMockVersion(versionNumber: Int): Version = {
    val mockVersion = mock[Version]

    (() => mockVersion.versionNumber).expects().returning(versionNumber).anyNumberOfTimes

    mockVersion
  }

  def getDbConfig() = TestConfig.dbConfigFromUrl(container.jdbcUrl, container.username, container.password)

  ".addVersion" should {
    "add to the versions queue in order" in {
      val dbConfig = getDbConfig()
      val migrator: Migrator = new Migrator(dbConfig)

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
      val dbConfig = getDbConfig()
      val migrator: Migrator = new Migrator(dbConfig)

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
      val dbConfig = getDbConfig()
      val migrator = new Migrator(dbConfig)

      DbUtil.tableExists(dbConfig, Migrator.COS_MIGRATIONS_TABLE) shouldBe false

      val result = migrator.beforeAll()
      result.isSuccess shouldBe true

      DbUtil.tableExists(dbConfig, Migrator.COS_MIGRATIONS_TABLE) shouldBe true
    }
    "writes the initial value if provided" in {
      setEnv(Migrator.COS_MIGRATIONS_INITIAL_VERSION, "3")

      val dbConfig = getDbConfig()
      val migrator = new Migrator(dbConfig)

      migrator.beforeAll().isSuccess shouldBe true

      Migrator.getCurrentVersionNumber(dbConfig) shouldBe Some(3)
    }
  }

  ".run" should {
    "run latest snapshot" in {
      val dbConfig = getDbConfig()

      // add some versions
      val version1 = getMockVersion(1)
      val version2 = getMockVersion(2)

      // version 2 should track that snapshot ran
      (() => version2.snapshot())
        .expects()
        .onCall(() => {
          DBIOAction.successful {}
        })
        .once()

      // define a migrator with two versions
      val migrator = new Migrator(dbConfig).addVersion(version1).addVersion(version2)

      val result = migrator.run()

      result.isSuccess shouldBe true

      Migrator.getCurrentVersionNumber(dbConfig) shouldBe Some(2)
    }
    "upgrade all available versions" in {
      val dbConfig = getDbConfig()

      // set db version number
      Migrator.createMigrationsTable(dbConfig)
      Await.ready(dbConfig.db.run(Migrator.setCurrentVersionNumber(dbConfig, 1, true)), Duration.Inf)
      Migrator.getCurrentVersionNumber(dbConfig) shouldBe Some(1)

      // define a migrator
      val migrator = new Migrator(dbConfig)

      // define 3 dynamic versions
      (2 to 4).foreach(versionNumber => {
        val version = getMockVersion(versionNumber)

        (() => version.beforeUpgrade()).expects().returning(Success {}).once()

        (() => version.upgrade()).expects().returning(DBIOAction.successful {}).once()

        (() => version.afterUpgrade()).expects().returning(Success {}).once()

        migrator.addVersion(version)
      })

      val result = migrator.run()

      result.isSuccess shouldBe true

      Migrator.getCurrentVersionNumber(dbConfig) shouldBe Some(4)
    }
    "no-op if no new versions to run" in {
      val dbConfig = getDbConfig()

      // define a migrator with versions that should not run (nothing mocked)
      val migrator =
        new Migrator(dbConfig).addVersion(getMockVersion(1)).addVersion(getMockVersion(2)).addVersion(getMockVersion(3))

      // set db version number to the highest version
      Migrator.createMigrationsTable(dbConfig)
      Await.ready(dbConfig.db.run(Migrator.setCurrentVersionNumber(dbConfig, 3, true)), Duration.Inf)
      Migrator.getCurrentVersionNumber(dbConfig) shouldBe Some(3)

      // run the migrator, confirm still at same version
      migrator.run().isSuccess shouldBe true
      Migrator.getCurrentVersionNumber(dbConfig) shouldBe Some(3)
    }
  }

  ".snapshotVersion" should {
    "run version snapshot and set version number" in {
      val dbConfig = getDbConfig()
      val versionNumber = 3

      // create a mock version that tracks if snapshot was run
      val someVersion = getMockVersion(versionNumber)

      (() => someVersion.snapshot()).expects().returning(DBIOAction.successful {}).once()

      // create the versions table
      Migrator.createMigrationsTable(dbConfig).isSuccess shouldBe true
      // confirm no prior version
      Migrator.getCurrentVersionNumber(dbConfig) shouldBe None
      // run and persist the snapshot
      Migrator.snapshotVersion(dbConfig, someVersion).isSuccess shouldBe true
      // confirm version number in DB
      Migrator.getCurrentVersionNumber(dbConfig) shouldBe Some(versionNumber)
    }
  }

  ".upgradeVersion" should {
    "run version upgrade and set version number" in {
      val dbConfig = getDbConfig()

      val versionNumber = 5

      // create a mock version that tracks if upgrade runs
      val version = getMockVersion(versionNumber)

      (() => version.beforeUpgrade()).expects().returning(Success {}).once()

      (() => version.upgrade()).expects().returning(DBIOAction.successful {}).once()

      (() => version.afterUpgrade()).expects().returning(Success {}).once()

      // create the versions table
      Migrator.createMigrationsTable(dbConfig).isSuccess shouldBe true
      val setStmt = Migrator.setCurrentVersionNumber(dbConfig, versionNumber - 1, true)
      Await.ready(dbConfig.db.run(setStmt), Duration.Inf)
      // confirm prior version
      Migrator.getCurrentVersionNumber(dbConfig) shouldBe Some(versionNumber - 1)
      // run and persist the snapshot
      Migrator.upgradeVersion(dbConfig, version).isSuccess shouldBe true
      // confirm version number in DB
      Migrator.getCurrentVersionNumber(dbConfig) shouldBe Some(versionNumber)
    }
    "fail if no prior version" in {
      val dbConfig = getDbConfig()
      // create the versions table
      Migrator.createMigrationsTable(dbConfig).isSuccess shouldBe true
      // confirm no prior version
      Migrator.getCurrentVersionNumber(dbConfig) shouldBe None
      // test failure
      val version: Version = getMockVersion(2)
      val actual = Migrator.upgradeVersion(dbConfig, version)
      actual.failed.map(_.getMessage().endsWith("no prior version, cannot upgrade")) shouldBe Success(true)
    }
    "fail if skipping versions" in {
      val dbConfig = getDbConfig()
      // create the versions table
      Migrator.createMigrationsTable(dbConfig).isSuccess shouldBe true
      // set prior version
      val setStmt = Migrator.setCurrentVersionNumber(dbConfig, 1, true)
      Await.ready(dbConfig.db.run(setStmt), Duration.Inf)
      // confirm no prior version
      Migrator.getCurrentVersionNumber(dbConfig) shouldBe Some(1)
      // test failure
      val version = getMockVersion(3)
      val actual = Migrator.upgradeVersion(dbConfig, version)
      actual.failed.map(_.getMessage().endsWith("cannot upgrade from version 1 to 3")) shouldBe Success(true)
    }
  }

  ".createMigrationsTable" should {
    "create the table if not exists" in {
      val dbConfig: DatabaseConfig[JdbcProfile] =
        dbConfigFromUrl(container.jdbcUrl, container.username, container.password)

      val actual = Migrator.createMigrationsTable(dbConfig)

      actual.isSuccess shouldBe true

      DbUtil.tableExists(dbConfig, Migrator.COS_MIGRATIONS_TABLE) shouldBe true
    }
    "no-op if table exists" in {
      val dbConfig: DatabaseConfig[JdbcProfile] =
        dbConfigFromUrl(container.jdbcUrl, container.username, container.password)

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
      val dbConfig: DatabaseConfig[JdbcProfile] =
        dbConfigFromUrl(container.jdbcUrl, container.username, container.password)

      // create the migrations table
      Migrator.createMigrationsTable(dbConfig).isSuccess shouldBe true

      val stmt = Migrator
        .setCurrentVersionNumber(dbConfig, 2, true)
        .andThen(Migrator.setCurrentVersionNumber(dbConfig, 3, false))
        .andThen(Migrator.setCurrentVersionNumber(dbConfig, 4, false))

      Await.ready(dbConfig.db.run(stmt), Duration.Inf)

      val actual: Option[Int] = Migrator.getCurrentVersionNumber(dbConfig)

      actual shouldBe Some(4)
    }
    "return None for no prior version" in {
      val dbConfig: DatabaseConfig[JdbcProfile] =
        dbConfigFromUrl(container.jdbcUrl, container.username, container.password)

      // create the migrations table
      Migrator.createMigrationsTable(dbConfig).isSuccess shouldBe true

      val actual: Option[Int] = Migrator.getCurrentVersionNumber(dbConfig)

      actual shouldBe None
    }
  }
  ".setCurrentVersionNumber" should {
    "write versions to db" in {
      val dbConfig: DatabaseConfig[JdbcProfile] =
        dbConfigFromUrl(container.jdbcUrl, container.username, container.password)

      // create the migrations table
      Migrator.createMigrationsTable(dbConfig).isSuccess shouldBe true

      val stmt = Migrator
        .setCurrentVersionNumber(dbConfig, 2, true)
        .andThen(Migrator.setCurrentVersionNumber(dbConfig, 3, false))

      Await.ready(dbConfig.db.run(stmt), Duration.Inf)

      val readFuture = dbConfig.db.run(sql"""
      select version_number, is_snapshot
      from #${Migrator.COS_MIGRATIONS_TABLE}
      """.as[(Int, Boolean)])

      val actual = Await.result(readFuture, Duration.Inf)

      actual.size shouldBe 2

      actual.toSeq should contain theSameElementsAs Seq((2, true), (3, false))
    }
  }

  ".setInitialVersion" should {
    "no-op if no env set" in {
      val dbConfig: DatabaseConfig[JdbcProfile] =
        dbConfigFromUrl(container.jdbcUrl, container.username, container.password)
      // create the migrations table
      Migrator.createMigrationsTable(dbConfig).isSuccess shouldBe true
      // write no value
      Migrator.setInitialVersion(dbConfig).isSuccess shouldBe true
      // read no value
      Migrator.getCurrentVersionNumber(dbConfig) shouldBe None
    }
    "sets an initial version" in {
      val dbConfig: DatabaseConfig[JdbcProfile] =
        dbConfigFromUrl(container.jdbcUrl, container.username, container.password)
      // set initial version as env var
      setEnv(Migrator.COS_MIGRATIONS_INITIAL_VERSION, "3")
      // create the migrations table
      Migrator.createMigrationsTable(dbConfig).isSuccess shouldBe true
      // write no value
      Migrator.setInitialVersion(dbConfig).isSuccess shouldBe true
      // read no value
      Migrator.getCurrentVersionNumber(dbConfig) shouldBe Some(3)
    }
    "prevents empty version number" in {
      val dbConfig: DatabaseConfig[JdbcProfile] =
        dbConfigFromUrl(container.jdbcUrl, container.username, container.password)
      // set initial version as env var
      setEnv(Migrator.COS_MIGRATIONS_INITIAL_VERSION, "")
      // create the migrations table
      Migrator.createMigrationsTable(dbConfig).isSuccess shouldBe true
      // write no value
      val actual = Migrator.setInitialVersion(dbConfig)
      // check error
      actual.isFailure shouldBe true
      actual.failed.map(_.getMessage.endsWith("setting provided empty")) shouldBe Success(true)
    }
    "prevents non-int version number" in {
      val dbConfig: DatabaseConfig[JdbcProfile] =
        dbConfigFromUrl(container.jdbcUrl, container.username, container.password)
      // set initial version as env var
      setEnv(Migrator.COS_MIGRATIONS_INITIAL_VERSION, "X")
      // create the migrations table
      Migrator.createMigrationsTable(dbConfig).isSuccess shouldBe true
      // write no value
      val actual = Migrator.setInitialVersion(dbConfig)
      // check error
      actual.isFailure shouldBe true
      actual.failed.map(_.getMessage.endsWith("cannot be 'X'")) shouldBe Success(true)
    }
  }
}
