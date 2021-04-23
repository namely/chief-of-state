/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v5

import com.namely.chiefofstate.migration.{ BaseSpec, DbUtil, SchemasUtil }
import com.dimafeng.testcontainers.{ ForAllTestContainer, PostgreSQLContainer }
import com.namely.chiefofstate.migration.helper.TestConfig
import org.testcontainers.utility.DockerImageName
import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import java.sql.{ Connection, DriverManager }
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import akka.actor.testkit.typed.scaladsl.ActorTestKit

class V5Spec extends BaseSpec with ForAllTestContainer {
  override val container: PostgreSQLContainer = PostgreSQLContainer
    .Def(dockerImageName = DockerImageName.parse("postgres"), urlParams = Map("currentSchema" -> V5Spec.cosSchema))
    .createContainer()

  lazy val journalJdbcConfig: DatabaseConfig[JdbcProfile] =
    TestConfig.dbConfigFromUrl(container.jdbcUrl, container.username, container.password, "write-side-slick")

  lazy val config: Config = ConfigFactory
    .parseResources("migration.conf")
    .withValue("write-side-slick.db.url", ConfigValueFactory.fromAnyRef(container.jdbcUrl))
    .withValue("write-side-slick.db.user", ConfigValueFactory.fromAnyRef(container.username))
    .withValue("write-side-slick.db.password", ConfigValueFactory.fromAnyRef(container.password))
    .withValue("write-side-slick.db.serverName", ConfigValueFactory.fromAnyRef(container.host))
    .withValue("write-side-slick.db.databaseName", ConfigValueFactory.fromAnyRef(container.databaseName))
    .resolve()

  lazy val testKit: ActorTestKit = ActorTestKit(config)

  override def beforeEach(): Unit = {
    V5Spec.recreateSchema(container)
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    testKit.shutdownTestKit()
  }

  ".snapshot" should {
    "create the new journal, snapshot and read side store" in {
      val version = V5(testKit.system, journalJdbcConfig)
      Await.result(journalJdbcConfig.db.run(version.snapshot()), Duration.Inf) shouldBe {}
      DbUtil.tableExists(journalJdbcConfig, "event_journal") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "event_tag") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "state_snapshot") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "read_side_offsets") shouldBe true
    }
  }

  ".migrateJournal" should {
    "upgrade the journal headers" in {
      // TODO
    }
  }

  ".migrateSnapshots" should {
    "upgrade the snapshot headers" in {
      // TODO
    }
  }
}

object V5Spec {

  val cosSchema: String = "cos"

  /**
   * create connection to the container db for test statements
   */
  def getConnection(container: PostgreSQLContainer): Connection = {
    // load the driver
    Class.forName("org.postgresql.Driver")

    DriverManager.getConnection(container.jdbcUrl, container.username, container.password)
  }

  // drop the COS schema between tests
  def recreateSchema(container: PostgreSQLContainer): Unit = {
    val conn = getConnection(container)
    val statement = conn.createStatement()
    statement.addBatch(s"drop schema if exists $cosSchema cascade")
    statement.addBatch(s"create schema $cosSchema")
    statement.executeBatch()
    conn.close()
  }

}
