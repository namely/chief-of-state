/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v5

import com.namely.chiefofstate.migration.{ BaseSpec, DbUtil, SchemasUtil }
import com.dimafeng.testcontainers.{ ForAllTestContainer, PostgreSQLContainer }
import com.namely.chiefofstate.migration.helper.{ DbHelper, TestConfig }
import org.testcontainers.utility.DockerImageName
import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import java.sql.{ Connection, DriverManager }
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import com.namely.protobuf.chiefofstate.v1.persistence.{ EventWrapper, StateWrapper }
import com.namely.protobuf.chiefofstate.v1.common.MetaData
import com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers.{ Header, Headers }
import com.google.protobuf.any

class V5Spec extends BaseSpec with ForAllTestContainer {

  val cosSchema: String = "cos"

  override val container: PostgreSQLContainer = PostgreSQLContainer
    .Def(dockerImageName = DockerImageName.parse("postgres:11"), urlParams = Map("currentSchema" -> cosSchema))
    .createContainer()

  lazy val journalJdbcConfig: DatabaseConfig[JdbcProfile] =
    TestConfig.dbConfigFromUrl(container.jdbcUrl, container.username, container.password, "write-side-slick")

  lazy val config: Config = ConfigFactory
    .parseResources("migration.conf")
    .withValue("write-side-slick.db.url", ConfigValueFactory.fromAnyRef(container.jdbcUrl))
    .withValue("write-side-slick.db.user", ConfigValueFactory.fromAnyRef(container.username))
    .withValue("write-side-slick.db.password", ConfigValueFactory.fromAnyRef(container.password))
    .withValue("write-side-slick.db.serverName", ConfigValueFactory.fromAnyRef(container.host))
    .withValue("write-side-slick.db.databaseName", ConfigValueFactory.fromAnyRef(container.databaseName))
    .resolve()

  lazy val testKit: ActorTestKit = ActorTestKit(config)

  override def beforeEach(): Unit = {
    DbHelper.recreateSchema(container, cosSchema)
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    testKit.shutdownTestKit()
  }

  ".snapshot" should {
    "create the new journal, snapshot and read side store" in {
      val version = V5(testKit.system, journalJdbcConfig)
      Await.result(journalJdbcConfig.db.run(version.snapshot()), Duration.Inf) shouldBe {}
      DbUtil.tableExists(journalJdbcConfig, "event_journal") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "event_tag") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "state_snapshot") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "read_side_offsets") shouldBe true
    }
  }

  ".beforeUpgrade" should {
    "upgrade the journal headers" in {
      val testConn = DbHelper.getConnection(container)

      val headers = Headers()
        .addHeaders(Header().withKey("1").withStringValue("one"))
        .addHeaders(Header().withKey("2").withStringValue("two"))

      val meta = MetaData().withData(Map(V5.pluginId -> any.Any.pack(headers)))

      val eventWrapper = EventWrapper().withMeta(meta)

      SchemasUtil.createStoreTables(journalJdbcConfig)

      val insertStmt = testConn.createStatement()
      insertStmt.addBatch(DbHelper.insertJournal(id = "1", payload = eventWrapper.toByteArray))
      insertStmt.addBatch(DbHelper.insertJournal(id = "2", payload = eventWrapper.toByteArray))
      insertStmt.addBatch(DbHelper.insertJournal(id = "3", payload = eventWrapper.toByteArray))

      insertStmt.executeBatch().sum shouldBe 3

      val version = V5(testKit.system, journalJdbcConfig)
      version.beforeUpgrade().isSuccess shouldBe true

      val resultStmt = testConn.createStatement()

      val results = resultStmt.executeQuery("""
        select distinct event_payload
        from event_journal
      """)

      results.next() shouldBe true
      val actual = EventWrapper.parseFrom(results.getBytes(1))
      results.next() shouldBe false
      testConn.close()

      actual.getMeta.headers.size shouldBe 2
      actual.getMeta.headers.find(_.key == "1").isDefined shouldBe true
      actual.getMeta.headers.find(_.key == "2").isDefined shouldBe true
    }
    "upgrade the snapshot headers" in {
      val testConn = DbHelper.getConnection(container)

      val headers = Headers()
        .addHeaders(Header().withKey("1").withStringValue("one"))
        .addHeaders(Header().withKey("2").withStringValue("two"))

      val meta = MetaData().withData(Map(V5.pluginId -> any.Any.pack(headers)))

      val stateWrapper = StateWrapper().withMeta(meta)

      SchemasUtil.createStoreTables(journalJdbcConfig)

      val insertStmt = testConn.createStatement()
      insertStmt.addBatch(DbHelper.insertSnapshot(id = "1", payload = stateWrapper.toByteArray))
      insertStmt.addBatch(DbHelper.insertSnapshot(id = "2", payload = stateWrapper.toByteArray))
      insertStmt.addBatch(DbHelper.insertSnapshot(id = "3", payload = stateWrapper.toByteArray))

      insertStmt.executeBatch().sum shouldBe 3

      val version = V5(testKit.system, journalJdbcConfig)
      version.beforeUpgrade().isSuccess shouldBe true

      val resultStmt = testConn.createStatement()

      val results = resultStmt.executeQuery("""
        select distinct snapshot_payload
        from state_snapshot
      """)

      results.next() shouldBe true
      val actual = StateWrapper.parseFrom(results.getBytes(1))
      results.next() shouldBe false
      testConn.close()

      actual.getMeta.headers.size shouldBe 2
      actual.getMeta.headers.find(_.key == "1").isDefined shouldBe true
      actual.getMeta.headers.find(_.key == "2").isDefined shouldBe true
    }
  }
}
