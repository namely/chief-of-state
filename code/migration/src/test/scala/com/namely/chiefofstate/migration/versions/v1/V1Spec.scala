/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v1

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import com.typesafe.config.Config
import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.sql.Connection
import com.namely.chiefofstate.migration.BaseSpec

class V1Spec extends BaseSpec with ForAllTestContainer {

  val testKit: ActorTestKit = ActorTestKit()

  val cosSchema: String = "cos"

  override val container: PostgreSQLContainer = PostgreSQLContainer
    .Def(
      dockerImageName = DockerImageName.parse("postgres"),
      urlParams = Map("currentSchema" -> cosSchema)
    )
    .createContainer()

  /**
   * create connection to the container db for test statements
   */
  def getConnection(): Connection = {
    // load the driver
    Class.forName("org.postgresql.Driver")

    DriverManager
      .getConnection(container.jdbcUrl, container.username, container.password)
  }

  def recreateSchema(): Unit = {
    val statement = getConnection().createStatement()
    statement.addBatch(s"drop schema if exists $cosSchema cascade")
    statement.addBatch(s"create schema $cosSchema")
    statement.executeBatch()
  }

  override def beforeEach() = {
    recreateSchema()
  }

  override protected def afterAll() = {
    testKit.shutdownTestKit()
  }

  ".beforeUpgrade" should {
    "migrate data into the new table" in {
      // TODO
    }
  }
  ".afterUpgrade" should {
    "drop the table on the old connection" in {
      // TODO
    }
    "do nothing if on the new connection" in {
      // TODO
    }
  }
  ".upgrade" should {
    "drop the old table and index" in {
      // TODO
    }
  }
}
