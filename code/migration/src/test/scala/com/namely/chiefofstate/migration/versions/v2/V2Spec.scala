package com.namely.chiefofstate.migration.versions.v2

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import com.typesafe.config.Config
import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.sql.Connection

class V2Spec extends BaseSpec with ForAllTestContainer {

  val testKit: ActorTestKit = ActorTestKit()

  val cosSchema: String = "cos"

  override val container: PostgreSQLContainer = PostgreSQLContainer
    .Def(
      dockerImageName = DockerImageName.parse("postgres"),
      urlParams = Map("currentSchema" -> cosSchema)
    )
    .createContainer()

  /** create connection to the container db for test statements */
  def getConnection(): Connection = {
    // load the driver
    Class.forName("org.postgresql.Driver")

    DriverManager
      .getConnection(container.jdbcUrl, container.username, container.password)
  }

  // helper to drop the schema
  def recreateSchema(): Unit = {
    val statement = getConnection().createStatement()
    statement.addBatch(s"drop schema if exists $cosSchema cascade")
    statement.addBatch(s"create schema $cosSchema")
    statement.executeBatch()
  }

  override def beforeEach() = {
    super.beforeEach()
    recreateSchema()
  }

  override protected def afterAll() = {
    super.afterAll()
    testKit.shutdownTestKit()
    clearEnv()
  }

  // TODO: add tests
}
