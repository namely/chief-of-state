/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import com.typesafe.config.{Config, ConfigFactory}
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres

import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.concurrent.duration.Duration

class CosSchemasSpec extends BaseSpec {
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

  "Given CosSchemas we" should {
    "create Journal, Snapshot, CosVersions and ReadSide stores" in {
      implicit val ec: ExecutionContextExecutor = testKit.system.executionContext
      val config: Config = ConfigFactory.parseResources("test.conf").resolve()
      noException shouldBe thrownBy(
        Await.result(CosSchemas.createIfNotExists(config)(testKit.system), Duration.Inf)
      )

      whenReady(CosSchemas.checkIfCosVersionTableExists(config)) { result: Seq[String] =>
        result.length shouldBe 1
        result.head shouldBe "cos_versions"
      }
    }

    "not have any legacy journal and snapshot" in {
      implicit val ec: ExecutionContextExecutor = testKit.system.executionContext
      val config: Config = ConfigFactory.parseResources("test.conf").resolve()
      whenReady(CosSchemas.checkIfLegacyTablesExist(config)) { result =>
        val (journalResult: Seq[String], snapshotResult: Seq[String]) = result
        journalResult.length shouldBe 1
        journalResult.headOption shouldBe Some(null)
        snapshotResult.length shouldBe 1
        snapshotResult.headOption shouldBe Some(null)
      }
    }
  }
}
