/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, BehaviorTestKit}
import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import com.google.protobuf.wrappers.StringValue
import com.namely.chiefofstate.helper.BaseSpec
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.testcontainers.utility.DockerImageName

import java.sql.{Connection, DriverManager, Statement}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class ServiceBootstrapperSpec extends BaseSpec with ForAllTestContainer {
  val cosSchema: String = "cos"

  val replyTimeout: FiniteDuration = FiniteDuration(30, TimeUnit.SECONDS)

  override val container: PostgreSQLContainer = PostgreSQLContainer
    .Def(
      dockerImageName = DockerImageName.parse("postgres"),
      urlParams = Map("currentSchema" -> cosSchema)
    )
    .createContainer()

  def recreateSchema(): Unit = {
    // load the driver
    Class.forName("org.postgresql.Driver")

    val connection: Connection = DriverManager
      .getConnection(container.jdbcUrl, container.username, container.password)

    val statement: Statement = connection.createStatement()
    statement.addBatch(s"drop schema if exists $cosSchema cascade")
    statement.addBatch(s"create schema $cosSchema")
    statement.executeBatch()
  }

  lazy val config: Config = ConfigFactory
    .parseResources("test.conf")
    .withValue("akka.projection.slick.db.url", ConfigValueFactory.fromAnyRef(container.jdbcUrl))
    .withValue("akka.projection.slick.db.user", ConfigValueFactory.fromAnyRef(container.username))
    .withValue("akka.projection.slick.db.password", ConfigValueFactory.fromAnyRef(container.password))
    .withValue("akka.projection.slick.db.serverName", ConfigValueFactory.fromAnyRef(container.host))
    .withValue("akka.projection.slick.db.databaseName", ConfigValueFactory.fromAnyRef(container.databaseName))
    .withValue("write-side-slick.db.url", ConfigValueFactory.fromAnyRef(container.jdbcUrl))
    .withValue("write-side-slick.db.user", ConfigValueFactory.fromAnyRef(container.username))
    .withValue("write-side-slick.db.password", ConfigValueFactory.fromAnyRef(container.password))
    .withValue("write-side-slick.db.serverName", ConfigValueFactory.fromAnyRef(container.host))
    .withValue("write-side-slick.db.databaseName", ConfigValueFactory.fromAnyRef(container.databaseName))
    .resolve()

  lazy val testKit: ActorTestKit = ActorTestKit(config)

  override def beforeEach(): Unit = {
    super.beforeEach()
    recreateSchema()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    testKit.shutdownTestKit()
  }

  "ServiceBootstrapper" should {

    "stop because of unhandled scalabp GeneratedMessage" in {
      // create an instance of ServiceMigrationRunner
      val boostrapper = BehaviorTestKit(ServiceBootstrapper(config))

      // send the migration command to the migrator
      boostrapper.run(StringValue("x"))

      boostrapper.isAlive shouldBe false
    }
  }
}
