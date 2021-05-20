/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v6
import com.dimafeng.testcontainers.{ ForAllTestContainer, PostgreSQLContainer }
import com.namely.chiefofstate.migration.{ BaseSpec, DbUtil }
import com.namely.chiefofstate.migration.helper.{ DbHelper, TestConfig }
import org.testcontainers.utility.DockerImageName
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.duration.Duration
import scala.concurrent.Await

class V6Spec extends BaseSpec with ForAllTestContainer {
  val cosSchema: String = "cos"

  override val container: PostgreSQLContainer = PostgreSQLContainer
    .Def(dockerImageName = DockerImageName.parse("postgres:11"), urlParams = Map("currentSchema" -> cosSchema))
    .createContainer()

  lazy val journalJdbcConfig: DatabaseConfig[JdbcProfile] =
    TestConfig.dbConfigFromUrl(container.jdbcUrl, container.username, container.password)

  override def beforeEach(): Unit = {
    DbHelper.recreateSchema(container, cosSchema)
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
  }

  ".snapshot" should {
    "create the new journal, snapshot and read side stores" in {
      val version = V6(journalJdbcConfig)
      Await.result(journalJdbcConfig.db.run(version.snapshot()), Duration.Inf) shouldBe {}
      DbUtil.tableExists(journalJdbcConfig, "event_journal") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "event_tag") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "state_snapshot") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "read_side_offsets") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "read_sides") shouldBe true
    }
  }

  ".upgrade" should {
    "only create the read_sides management table" in {
      val version = V6(journalJdbcConfig)
      DbUtil.tableExists(journalJdbcConfig, "read_sides") shouldBe false
      Await.ready(journalJdbcConfig.db.run(version.upgrade()), Duration.Inf)
      DbUtil.tableExists(journalJdbcConfig, "read_sides") shouldBe true
    }
  }
}
