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

class SchemasSpec extends BaseSpec {
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

  "Given CreateSchemas we" should {
    "create Journal, Snapshot, CosVersions and ReadSide stores" in {
      implicit val ec: ExecutionContextExecutor = testKit.system.executionContext
      val config: Config = ConfigFactory.parseResources("test.conf").resolve()
      val journalJdbcConfig: DatabaseConfig[JdbcProfile] = JdbcConfig.journalConfig(config)
      val readSideJdbcConfig: DatabaseConfig[PostgresProfile] = JdbcConfig.projectionConfig(config)
      val createSchemas: CreateSchemas = CreateSchemas(journalJdbcConfig, readSideJdbcConfig)
      val dbQuery: DbQuery = DbQuery(config, journalJdbcConfig)
      noException shouldBe thrownBy(
        Await.result(createSchemas.journalStoresIfNotExists(), Duration.Inf)
      )
      Await.result(dbQuery.checkIfCosMigrationsTableExists(), Duration.Inf) shouldBe true
    }

    "not have any legacy journal and snapshot" in {
      implicit val ec: ExecutionContextExecutor = testKit.system.executionContext
      val config: Config = ConfigFactory.parseResources("test.conf").resolve()

      val dbconfig: DatabaseConfig[JdbcProfile] = JdbcConfig.journalConfig(config)
      val dbQuery: DbQuery = DbQuery(config, dbconfig)
      Await.result(dbQuery.checkIfLegacyTablesExist(), Duration.Inf) shouldBe false
    }
  }

  "Given DropSchemas we" should {
    "drop created journal and snapshot data stores" in {
      implicit val ec: ExecutionContextExecutor = testKit.system.executionContext
      val config: Config = ConfigFactory.parseResources("test.conf").resolve()
      val journalJdbcConfig: DatabaseConfig[JdbcProfile] = JdbcConfig.journalConfig(config)
      val readSideJdbcConfig: DatabaseConfig[PostgresProfile] = JdbcConfig.projectionConfig(config)
      val createSchemas: CreateSchemas = CreateSchemas(journalJdbcConfig, readSideJdbcConfig)
      val dbQuery: DbQuery = DbQuery(config, journalJdbcConfig)
      val dropSchemas: DropSchemas = DropSchemas(journalJdbcConfig)

      noException shouldBe thrownBy(
        Await.result(createSchemas.journalStoresIfNotExists(), Duration.Inf)
      )

      Await.result(dbQuery.checkIfCosMigrationsTableExists(), Duration.Inf) shouldBe true
      Await.result(dropSchemas.journalStoresIfExist(), Duration.Inf) shouldBe ()
    }
  }
}
