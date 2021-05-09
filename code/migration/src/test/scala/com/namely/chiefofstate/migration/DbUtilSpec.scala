/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration

import com.dimafeng.testcontainers.{ ForAllTestContainer, PostgreSQLContainer }
import com.namely.chiefofstate.migration.helper.TestConfig
import org.testcontainers.utility.DockerImageName

import java.sql.DriverManager

class DbUtilSpec extends BaseSpec with ForAllTestContainer {
  override val container: PostgreSQLContainer =
    PostgreSQLContainer.Def(dockerImageName = DockerImageName.parse("postgres:11")).createContainer()

  def connection = {
    // load the driver
    Class.forName("org.postgresql.Driver")

    DriverManager.getConnection(container.jdbcUrl, container.username, container.password)
  }

  def dbConfig = TestConfig.dbConfigFromUrl(container.jdbcUrl, container.username, container.password)

  ".tableExists" should {
    "return true if table exists" in {
      val statement = connection.createStatement()
      statement.addBatch(s"create table real_table(id int)")
      statement.executeBatch()

      DbUtil.tableExists(dbConfig, "real_table") shouldBe true
    }
    "return false for missing table" in {
      DbUtil.tableExists(dbConfig, "fake_table") shouldBe false
    }
  }
}
