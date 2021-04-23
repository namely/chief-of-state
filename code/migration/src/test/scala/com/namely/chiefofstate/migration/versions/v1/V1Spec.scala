/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v1

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import com.dimafeng.testcontainers.{ ForAllTestContainer, MultipleContainers, PostgreSQLContainer }
import com.namely.chiefofstate.migration.{ BaseSpec, DbUtil }
import com.namely.chiefofstate.migration.helper.TestConfig
import com.namely.chiefofstate.migration.versions.v1.V1.{ createTable, insertInto, tempTable, OffsetRow }
import com.typesafe.config.Config
import org.testcontainers.utility.DockerImageName
import slick.basic.DatabaseConfig
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlStreamingAction

import java.sql.{ Connection, DriverManager }
import java.time.Instant
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import com.namely.chiefofstate.migration.helper.DbHelper._

class V1Spec extends BaseSpec with ForAllTestContainer {

  val testKit: ActorTestKit = ActorTestKit()

  val cosSchema: String = "cos"

  val projectionPg: PostgreSQLContainer = PostgreSQLContainer
    .Def(dockerImageName = DockerImageName.parse("postgres"), urlParams = Map("currentSchema" -> cosSchema))
    .createContainer()

  val journalPg: PostgreSQLContainer = PostgreSQLContainer
    .Def(dockerImageName = DockerImageName.parse("postgres"), urlParams = Map("currentSchema" -> cosSchema))
    .createContainer()

  override val container: MultipleContainers = MultipleContainers(projectionPg, journalPg)

  // old projection jdbc config
  lazy val projectionJdbcConfig: DatabaseConfig[JdbcProfile] = {
    val cfg: Config =
      TestConfig.getProjectionConfig(projectionPg.jdbcUrl, projectionPg.username, projectionPg.password, false)
    DatabaseConfig.forConfig[JdbcProfile]("akka.projection.slick", cfg)
  }

  // journal jdbc config
  lazy val journalJdbcConfig: DatabaseConfig[JdbcProfile] =
    TestConfig.dbConfigFromUrl(journalPg.jdbcUrl, journalPg.username, journalPg.password, "write-side-slick")

  /**
   * creates the read side offset store table
   *
   * @param projectionJdbcConfig the the projection db config
   */
  def createOldOffsetTable(
      projectionJdbcConfig: DatabaseConfig[JdbcProfile],
      tableName: String = "read_side_offsets",
      indexName: String = "projection_name_index"): Unit = {
    val stmt = DBIO.seq(
      sqlu"""
        CREATE TABLE #$tableName(
          "PROJECTION_NAME" VARCHAR(255) NOT NULL,
          "PROJECTION_KEY" VARCHAR(255) NOT NULL,
          "CURRENT_OFFSET" VARCHAR(255) NOT NULL,
          "MANIFEST" VARCHAR(4) NOT NULL,
          "MERGEABLE" BOOLEAN NOT NULL,
          "LAST_UPDATED" BIGINT NOT NULL,
          PRIMARY KEY("PROJECTION_NAME", "PROJECTION_KEY")
        )""",
      sqlu"""CREATE INDEX #$indexName ON #$tableName ("PROJECTION_NAME")""")

    Await.result(projectionJdbcConfig.db.run(stmt), Duration.Inf)
  }

  /**
   * insert some data into the offset stores table
   *
   * @param projectionJdbcConfig the projection db config
   */
  def insert(projectionJdbcConfig: DatabaseConfig[JdbcProfile]): Int = {
    val data: Seq[OffsetRow] = Seq(
      OffsetRow(
        projectionName = "some-projection",
        projectionKey = "chiefofstate0",
        currentOffset = "38",
        manifest = "SEQ",
        mergeable = false,
        lastUpdated = 1614684500617L),
      OffsetRow(
        projectionName = "some-projection",
        projectionKey = "chiefofstate1",
        currentOffset = "123",
        manifest = "SEQ",
        mergeable = false,
        lastUpdated = 1614684500618L),
      OffsetRow(
        projectionName = "some-projection",
        projectionKey = "chiefofstate8",
        currentOffset = "167",
        manifest = "SEQ",
        mergeable = false,
        lastUpdated = Instant.now().toEpochMilli))
    insertInto("read_side_offsets", projectionJdbcConfig, data)
  }

  /**
   * get the number of records in the temp table
   *
   * @param journalJdbcConfig the database connection
   * @return the number of records
   */
  def count(journalJdbcConfig: DatabaseConfig[JdbcProfile]): Int = {
    val sqlStmt: SqlStreamingAction[Vector[Int], Int, Effect] =
      sql"""
            SELECT count(c.*)
            FROM #$tempTable as c
        """.as[Int]

    Await.result(journalJdbcConfig.db.run(sqlStmt), Duration.Inf).headOption.getOrElse(0)
  }

  override def beforeEach(): Unit = {
    recreateSchema(journalPg, cosSchema)
    recreateSchema(projectionPg, cosSchema)
  }

  override protected def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }

  ".beforeUpgrade" should {
    "create temporary table and migrate data into the new table" in {
      val v1: V1 = V1(journalJdbcConfig, projectionJdbcConfig, "read_side_offsets")

      // create the old read side offset store
      noException shouldBe thrownBy(createOldOffsetTable(projectionJdbcConfig))

      // seed the old read side offset store with some data
      insert(projectionJdbcConfig) shouldBe 3

      // then run beforeUpgrade
      v1.beforeUpgrade().isSuccess shouldBe true

      count(journalJdbcConfig) shouldBe 3
    }
    "succeed if old table does not exist" in {
      val v1: V1 = V1(journalJdbcConfig, projectionJdbcConfig, "not_a_table")
      // then run beforeUpgrade
      v1.beforeUpgrade().isSuccess shouldBe true
      // assert no records
      count(journalJdbcConfig) shouldBe 0
    }
  }
  ".migrate" should {
    "succeed even if old table does not exist" in {
      val v1: V1 = V1(journalJdbcConfig, projectionJdbcConfig, "not_a_table")
      // create the old read side offset store
      noException shouldBe thrownBy(v1.migrate())
    }
  }
  ".upgrade" should {
    "drop the old table and index" in {
      // given an instance of V1 version
      val v1: V1 = V1(journalJdbcConfig, projectionJdbcConfig, "read_side_offsets")

      // create the temp table
      V1.createTable(journalJdbcConfig) shouldBe {}

      // before upgrade the read_side_offset table does not exist on the new connection
      DbUtil.tableExists(journalJdbcConfig, "read_side_offsets") shouldBe false

      Await.result(journalJdbcConfig.db.run(v1.upgrade()), Duration.Inf) shouldBe {}

      // after the read_side_offset table does exist on the new connection
      DbUtil.tableExists(journalJdbcConfig, "read_side_offsets") shouldBe true
    }

    "drop the old table and index if exists on new connection" in {

      val oldTableName = "old_read_side"

      // given an instance of V1 version
      val v1: V1 = V1(journalJdbcConfig, projectionJdbcConfig, oldTableName)

      // create the old read side offset store
      createOldOffsetTable(journalJdbcConfig, oldTableName)
      DbUtil.tableExists(journalJdbcConfig, oldTableName) shouldBe true

      // create the temp table
      createTable(journalJdbcConfig)
      DbUtil.tableExists(journalJdbcConfig, V1.tempTable) shouldBe true

      // before upgrade the read_side_offset table does not exist on the new connection
      DbUtil.tableExists(journalJdbcConfig, V1.offsetTableName) shouldBe false

      // run the upgrade
      Await.result(journalJdbcConfig.db.run(v1.upgrade()), Duration.Inf)

      // after upgrade the read_side_offset table does exist on the new connection
      DbUtil.tableExists(journalJdbcConfig, V1.offsetTableName) shouldBe true

      // after upgrade, the old table should be gone
      DbUtil.tableExists(journalJdbcConfig, oldTableName) shouldBe false
    }
  }
}
