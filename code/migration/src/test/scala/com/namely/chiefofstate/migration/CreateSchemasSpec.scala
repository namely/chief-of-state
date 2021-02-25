/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import com.typesafe.config.{Config, ConfigFactory}
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import slick.basic.DatabaseConfig
import slick.jdbc.{JdbcProfile, PostgresProfile}

import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.concurrent.duration.Duration

class CreateSchemasSpec extends BaseSpec {
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
      val writeSideJdbcConfig: DatabaseConfig[JdbcProfile] = JdbcConfig.journalConfig(config)
      val readSideJdbcConfig: DatabaseConfig[PostgresProfile] = JdbcConfig.projectionConfig(config)
      val createSchemas: CreateSchemas = CreateSchemas(writeSideJdbcConfig, readSideJdbcConfig)
      val dbQuery: DbQuery = DbQuery(config, writeSideJdbcConfig)

      noException shouldBe thrownBy(
        Await.result(createSchemas.ifNotExists()(testKit.system), Duration.Inf)
      )

      whenReady(dbQuery.checkIfCosMigrationsTableExists()) { result: Seq[String] =>
        result.length shouldBe 1
        result.head shouldBe "cos_migrations"
      }
    }

    "not have any legacy journal and snapshot" in {
      implicit val ec: ExecutionContextExecutor = testKit.system.executionContext
      val config: Config = ConfigFactory.parseResources("test.conf").resolve()

      val dbconfig: DatabaseConfig[JdbcProfile] = JdbcConfig.journalConfig(config)
      val dbQuery: DbQuery = DbQuery(config, dbconfig)

      whenReady(dbQuery.checkIfLegacyTablesExist()) { result =>
        val (journalResult: Seq[String], snapshotResult: Seq[String]) = result
        journalResult.length shouldBe 1
        journalResult.headOption shouldBe Some(null)
        snapshotResult.length shouldBe 1
        snapshotResult.headOption shouldBe Some(null)
      }
    }
  }
}
