/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.readside

import com.namely.chiefofstate.helper.BaseSpec
import javax.sql.DataSource
import akka.actor.typed.ActorSystem
import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }
import akka.actor.testkit.typed.scaladsl.ActorTestKit

class ReadSideProjectionSpec extends BaseSpec {
  lazy val config: Config = ConfigFactory
    .parseResources("test.conf")
    .withValue("write-side-slick.db.url", ConfigValueFactory.fromAnyRef("fake-url"))
    .withValue("write-side-slick.db.user", ConfigValueFactory.fromAnyRef("user"))
    .withValue("write-side-slick.db.password", ConfigValueFactory.fromAnyRef("password"))
    .withValue("write-side-slick.db.serverName", ConfigValueFactory.fromAnyRef("host"))
    .withValue("write-side-slick.db.databaseName", ConfigValueFactory.fromAnyRef("postgres"))
    .resolve()

  lazy val testKit: ActorTestKit = ActorTestKit(config)

  val actorSystem = testKit.system

  override protected def afterAll(): Unit = {
    super.afterAll()
    testKit.shutdownTestKit()
  }

  ".jdbcProjection" should {
    "run without failure" in {
      val projectionId = "some-projection"
      val dataSource: DataSource = mock[DataSource]
      val readHandler: ReadSideHandler = mock[ReadSideHandler]
      val numShards = 2
      val projection = new ReadSideProjection(actorSystem, projectionId, dataSource, readHandler, numShards)
      val tagName: String = "1"
      projection.jdbcProjection(tagName)
    }
  }
  ".sourceProvider" should {
    "run without failure" in {
      noException shouldBe thrownBy(ReadSideProjection.sourceProvider(actorSystem, "1"))
    }
  }
}
