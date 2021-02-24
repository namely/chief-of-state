/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import com.namely.chiefofstate.helper.BaseSpec
import com.typesafe.config.{Config, ConfigFactory}
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres

class SchemasUtilSpec extends BaseSpec {
  var pg: EmbeddedPostgres = null
  val testKit: ActorTestKit = ActorTestKit()
  override protected def beforeAll() = {
    val builder: EmbeddedPostgres.Builder = EmbeddedPostgres.builder()
    builder.setPort(25432)
    pg = builder.start()
  }

  override protected def afterAll() = {
    pg.close()
    testKit.shutdownTestKit()
  }

  ".create Journal and Snapshot store" in {

    val config: Config = ConfigFactory.parseResources("schemas-util.conf").resolve()
    noException shouldBe thrownBy(SchemasUtil.createIfNotExists(config)(testKit.system))
  }
}
