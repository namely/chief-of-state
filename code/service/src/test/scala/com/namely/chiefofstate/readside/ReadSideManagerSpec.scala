/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.readside

import com.namely.chiefofstate.helper.{ BaseSpec, ExecutionContextHelper }
import com.dimafeng.testcontainers.{ ForAllTestContainer, PostgreSQLContainer }
import org.testcontainers.utility.DockerImageName
import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }
import akka.actor.testkit.typed.scaladsl.ActorTestKit

class ReadSideManagerSpec extends BaseSpec with ForAllTestContainer with ExecutionContextHelper {

  val cosSchema: String = "cos"

  override val container: PostgreSQLContainer = PostgreSQLContainer
    .Def(dockerImageName = DockerImageName.parse("postgres"), urlParams = Map("currentSchema" -> cosSchema))
    .createContainer()

  lazy val config: Config = ConfigFactory
    .parseResources("test.conf")
    .withValue("jdbc-default.url", ConfigValueFactory.fromAnyRef(container.jdbcUrl))
    .withValue("jdbc-default.user", ConfigValueFactory.fromAnyRef(container.username))
    .withValue("jdbc-default.password", ConfigValueFactory.fromAnyRef(container.password))
    .withValue("jdbc-default.hikari-settings.max-pool-size", ConfigValueFactory.fromAnyRef(1))
    .withValue("jdbc-default.hikari-settings.min-idle-connections", ConfigValueFactory.fromAnyRef(1))
    .withValue("jdbc-default.hikari-settings.idle-timeout-ms", ConfigValueFactory.fromAnyRef(1000))
    .withValue("jdbc-default.hikari-settings.max-lifetime-ms", ConfigValueFactory.fromAnyRef(3000))
    .withValue("chief-of-state.read-side.enabled", ConfigValueFactory.fromAnyRef(true))
    .resolve()

  lazy val testKit: ActorTestKit = ActorTestKit(config)

  lazy val actorSystem = testKit.system

  override def afterAll(): Unit = {
    super.afterAll()
    testKit.shutdownTestKit()
  }

  ".apply" should {
    "construct without failure" in {
      noException shouldBe thrownBy(ReadSideManager(actorSystem, Seq(), 2))
    }
  }
  ".getDataSource" should {
    "return a hikari data source" in {
      val dbConfig = ReadSideManager.DbConfig(
        jdbcUrl = container.jdbcUrl,
        username = container.username,
        password = container.password,
        maxPoolSize = 1,
        minIdleConnections = 1,
        idleTimeoutMs = 1000,
        maxLifetimeMs = 3000)
      val dataSource = ReadSideManager.getDataSource(dbConfig)

      noException shouldBe thrownBy(dataSource.getConnection().close())
    }
  }
}
