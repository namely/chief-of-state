/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v1

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import com.dimafeng.testcontainers.{ForAllTestContainer, MultipleContainers, PostgreSQLContainer}
import com.namely.chiefofstate.migration.{BaseSpec, DbUtil}
import com.namely.chiefofstate.migration.helper.TestConfig
import com.namely.chiefofstate.migration.versions.v1.V1.{
  createTempTable,
  getOffSetRowResult,
  insertInto,
  OFFSET_STORE_TEMP_TABLE,
  OffsetRow
}
import com.namely.chiefofstate.migration.Migrator.createMigrationsTable
import com.typesafe.config.Config
import org.testcontainers.utility.DockerImageName
import slick.basic.DatabaseConfig
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlStreamingAction

import java.sql.{Connection, DriverManager}
import java.time.Instant
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class V1Spec extends BaseSpec with ForAllTestContainer {

  val testKit: ActorTestKit = ActorTestKit()

  val cosSchema: String = "cos"

  val projectionPg: PostgreSQLContainer = PostgreSQLContainer
    .Def(
      dockerImageName = DockerImageName.parse("postgres"),
      urlParams = Map("currentSchema" -> cosSchema)
    )
    .createContainer()

  val journalPg: PostgreSQLContainer = PostgreSQLContainer
    .Def(
      dockerImageName = DockerImageName.parse("postgres"),
      urlParams = Map("currentSchema" -> cosSchema)
    )
    .createContainer()

  override val container: MultipleContainers = MultipleContainers(projectionPg, journalPg)

  /**
   * create connection to the container db for test statements
   */
  def getConnection(container: PostgreSQLContainer): Connection = {
    // load the driver
    Class.forName("org.postgresql.Driver")

    DriverManager
      .getConnection(container.jdbcUrl, container.username, container.password)
  }

  def recreateSchema(container: PostgreSQLContainer): Unit = {
    val statement = getConnection(container).createStatement()
    statement.addBatch(s"drop schema if exists $cosSchema cascade")
    statement.addBatch(s"create schema $cosSchema")
    statement.executeBatch()
  }

  // old projection jdbc config
  lazy val projectionJdbcConfig: DatabaseConfig[JdbcProfile] = {
    val cfg: Config = TestConfig.getProjectionConfig(
      projectionPg.jdbcUrl,
      projectionPg.username,
      projectionPg.password,
      "read_side_offsets",
      false
    )
    DatabaseConfig.forConfig[JdbcProfile]("akka.projection.slick", cfg)
  }

  // journal jdbc config
  lazy val journalJdbcConfig: DatabaseConfig[JdbcProfile] = TestConfig.dbConfigFromUrl(
    journalPg.jdbcUrl,
    journalPg.username,
    journalPg.password,
    "write-side-slick"
  )

  def createOldStore(projectionJdbcConfig: DatabaseConfig[JdbcProfile]): Unit = {
    val stmt = DBIO.seq(
      sqlu"""
        CREATE TABLE IF NOT EXISTS read_side_offsets(
         "PROJECTION_NAME" VARCHAR(255) NOT NULL,
        "PROJECTION_KEY" VARCHAR(255) NOT NULL,
        "CURRENT_OFFSET" VARCHAR(255) NOT NULL,
        "MANIFEST" VARCHAR(4) NOT NULL,
        "MERGEABLE" BOOLEAN NOT NULL,
        "LAST_UPDATED" BIGINT NOT NULL,
        PRIMARY KEY("PROJECTION_NAME", "PROJECTION_KEY")
        )""",
      sqlu"""CREATE INDEX IF NOT EXISTS "PROJECTION_NAME_INDEX" ON read_side_offsets ("PROJECTION_NAME")"""
    )

    Await.result(projectionJdbcConfig.db.run(stmt), Duration.Inf)
  }

  def seedOldStore(projectionJdbcConfig: DatabaseConfig[JdbcProfile]): Int = {
    val data: Seq[OffsetRow] = Seq(
      OffsetRow(
        projectionName = "some-projection",
        projectionKey = "chiefofstate0",
        offsetStr = "38",
        manifest = "SEQ",
        mergeable = false,
        lastUpdated = 1614684500617L
      ),
      OffsetRow(
        projectionName = "some-projection",
        projectionKey = "chiefofstate1",
        offsetStr = "123",
        manifest = "SEQ",
        mergeable = false,
        lastUpdated = 1614684500618L
      ),
      OffsetRow(
        projectionName = "some-projection",
        projectionKey = "chiefofstate8",
        offsetStr = "167",
        manifest = "SEQ",
        mergeable = false,
        lastUpdated = Instant.now().toEpochMilli
      )
    )
    insertInto("read_side_offsets", projectionJdbcConfig, data)
  }

  def queryTempTable(journalJdbcConfig: DatabaseConfig[JdbcProfile]): Int = {
    val sqlStmt: SqlStreamingAction[Vector[OffsetRow], OffsetRow, Effect] =
      sql"""
            SELECT
                c.projection_name,
                c.projection_key,
                c.current_offset,
                c.manifest,
                c.mergeable,
                c.last_updated
            FROM #$OFFSET_STORE_TEMP_TABLE as c
        """.as[OffsetRow]

    Await.result(journalJdbcConfig.db.run(sqlStmt), Duration.Inf).length
  }

  override def beforeEach() = {
    recreateSchema(journalPg)
    recreateSchema(projectionPg)
  }

  override protected def afterAll() = {
    testKit.shutdownTestKit()
  }

  ".beforeUpgrade" should {
    "create temporary table and migrate data into the new table" in {
      val v1: V1 = V1(journalJdbcConfig, projectionJdbcConfig, "read_side_offsets")

      // create the old read side offset store
      noException shouldBe thrownBy(createOldStore(projectionJdbcConfig))

      // seed the old read side offset store with some data
      seedOldStore(projectionJdbcConfig) shouldBe 3

      // then run beforeUpgrade
      v1.beforeUpgrade().isSuccess shouldBe true

      queryTempTable(journalJdbcConfig) shouldBe 3
    }
  }
  ".afterUpgrade" should {
    "drop the table on the old connection" in {
      // given an instance of V1 version
      val v1: V1 = V1(journalJdbcConfig, projectionJdbcConfig, "read_side_offsets")

      // create the actual offset store table
      // create the old read side offset store
      noException shouldBe thrownBy(createOldStore(projectionJdbcConfig))

      v1.afterUpgrade().isSuccess shouldBe true

      DbUtil.tableExists(projectionJdbcConfig, "read_side_offsets") shouldBe false

    }
    "do nothing if on the new connection" in {
      // given an instance of V1 version
      val v1: V1 = V1(journalJdbcConfig, projectionJdbcConfig, "read_side_offsets")

      // create cos migrations table
      noException shouldBe thrownBy(createMigrationsTable(projectionJdbcConfig))

      // create the actual offset store table
      // create the old read side offset store
      noException shouldBe thrownBy(createOldStore(projectionJdbcConfig))

      v1.afterUpgrade().isSuccess shouldBe true
    }
  }
  ".upgrade" should {
    "drop the old table and index" in {
      // given an instance of V1 version
      val v1: V1 = V1(journalJdbcConfig, projectionJdbcConfig, "read_side_offsets")

      // create the temp table
      createTempTable(journalJdbcConfig) shouldBe {}

      // before upgrade the read_side_offset table does not exist on the new connection
      DbUtil.tableExists(journalJdbcConfig, "read_side_offsets") shouldBe false

      Await.result(journalJdbcConfig.db.run(v1.upgrade()), Duration.Inf) shouldBe {}

      // after the read_side_offset table does exist on the new connection
      DbUtil.tableExists(journalJdbcConfig, "read_side_offsets") shouldBe true
    }

    "drop the old table and index if exists on new connection" in {
      // given an instance of V1 version
      val v1: V1 = V1(journalJdbcConfig, projectionJdbcConfig, "read_side_offsets")

      // create the old read side offset store
      noException shouldBe thrownBy(createOldStore(journalJdbcConfig))

      // create the temp table
      createTempTable(journalJdbcConfig) shouldBe {}

      // before upgrade the read_side_offset table does not exist on the new connection
      DbUtil.tableExists(journalJdbcConfig, "read_side_offsets") shouldBe true

      Await.result(journalJdbcConfig.db.run(v1.upgrade()), Duration.Inf) shouldBe {}

      // after upgrade the read_side_offset table does exist on the new connection
      DbUtil.tableExists(journalJdbcConfig, "read_side_offsets") shouldBe true
    }
  }
}
