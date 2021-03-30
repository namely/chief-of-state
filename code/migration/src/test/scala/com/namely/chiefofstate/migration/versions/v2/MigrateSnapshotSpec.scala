/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v2

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.persistence.jdbc.config.SnapshotConfig
import akka.persistence.jdbc.db.SlickExtension
import akka.persistence.jdbc.snapshot.dao
import akka.persistence.jdbc.snapshot.dao.legacy.{ByteArraySnapshotDao, SnapshotQueries}
import akka.serialization.{Serialization, SerializationExtension}
import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import com.namely.chiefofstate.migration.{BaseSpec, DbUtil, JdbcConfig}
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.testcontainers.utility.DockerImageName
import slick.basic.DatabaseConfig
import slick.jdbc.{JdbcBackend, JdbcProfile}
import slick.jdbc.PostgresProfile.api._

import java.sql.{Connection, DriverManager}
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.concurrent.duration.Duration
import com.namely.chiefofstate.migration.versions.v3.SchemasUtil

class MigrateSnapshotSpec extends BaseSpec with ForAllTestContainer {
  val cosSchema: String = "cos"

  override val container: PostgreSQLContainer = PostgreSQLContainer
    .Def(
      dockerImageName = DockerImageName.parse("postgres"),
      urlParams = Map("currentSchema" -> cosSchema)
    )
    .createContainer()

  /**
   * create connection to the container db for test statements
   */
  def getConnection: Connection = {
    // load the driver
    Class.forName("org.postgresql.Driver")

    DriverManager
      .getConnection(container.jdbcUrl, container.username, container.password)
  }

  // helper to drop the schema
  def recreateSchema(): Unit = {
    val statement = getConnection.createStatement()
    statement.addBatch(s"drop schema if exists $cosSchema cascade")
    statement.addBatch(s"create schema $cosSchema")
    statement.executeBatch()
  }

  def countLegacySnapshot(journalJdbcConfig: DatabaseConfig[JdbcProfile], queries: SnapshotQueries): Int = {
    val q: DBIO[Int] = queries.SnapshotTable.length.result
    Await.result(journalJdbcConfig.db.run(q), Duration.Inf)
  }

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
    recreateSchema()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    testKit.shutdownTestKit()
  }

  "MigrateSnapshot" should {
    "migrate legacy snapshot data into the new snapshot table" in {
      implicit val ec: ExecutionContextExecutor = testKit.system.executionContext
      implicit val sys: ActorSystem[Nothing] = testKit.system
      val journalJdbcConfig: DatabaseConfig[JdbcProfile] = JdbcConfig.journalConfig(config)
      val profile: JdbcProfile = journalJdbcConfig.profile
      val snapshotConfig: SnapshotConfig = new SnapshotConfig(config.getConfig("jdbc-snapshot-store"))
      val queries: SnapshotQueries = new SnapshotQueries(profile, snapshotConfig.legacySnapshotTableConfiguration)
      val newQueries: dao.SnapshotQueries = new dao.SnapshotQueries(profile, snapshotConfig.snapshotTableConfiguration)
      val serialization: Serialization = SerializationExtension(testKit.system)

      val snapshotdb: JdbcBackend.Database =
        SlickExtension(testKit.system).database(config.getConfig("jdbc-snapshot-store")).database

      val legacySnapshotDao: ByteArraySnapshotDao =
        new ByteArraySnapshotDao(snapshotdb, profile, snapshotConfig, serialization)

      val migrator: MigrateSnapshot = MigrateSnapshot(
        testKit.system,
        profile,
        serialization,
        pageSize = 2
      )

      // let us create the legacy tables
      noException shouldBe thrownBy(SchemasUtil.createLegacyJournalAndSnapshot(journalJdbcConfig))
      DbUtil.tableExists(journalJdbcConfig, "snapshot") shouldBe true

      // let us load some data into the legacy snapshot
      noException shouldBe thrownBy(Seed.legacySnapshot(legacySnapshotDao))

      // let us count the legacy snapshot
      countLegacySnapshot(journalJdbcConfig, queries) shouldBe 4

      // let us create the new snapshot table
      noException shouldBe thrownBy(SchemasUtil.createJournalTables(journalJdbcConfig))

      DbUtil.tableExists(journalJdbcConfig, "state_snapshot") shouldBe true

      // let us migrate the data
      noException shouldBe thrownBy(migrator.migrate())

      // let us get the number of records in the new snapshot
      Await.result(journalJdbcConfig.db
                     .run(newQueries.SnapshotTable.length.result),
                   Duration.Inf
      ) shouldBe countLegacySnapshot(journalJdbcConfig, queries)
    }
  }
}
