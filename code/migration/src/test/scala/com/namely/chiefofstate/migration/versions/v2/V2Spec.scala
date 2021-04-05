/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v2

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.persistence.jdbc.config.{JournalConfig, SnapshotConfig}
import akka.persistence.jdbc.db.SlickExtension
import akka.persistence.jdbc.journal.dao.{legacy, JournalQueries}
import akka.persistence.jdbc.snapshot.dao
import akka.persistence.jdbc.snapshot.dao.legacy.{ByteArraySnapshotDao, SnapshotQueries}
import akka.serialization.{Serialization, SerializationExtension}
import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import com.namely.chiefofstate.migration.{BaseSpec, DbUtil, JdbcConfig, SchemasUtil}
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.testcontainers.utility.DockerImageName
import slick.basic.DatabaseConfig
import slick.jdbc.{JdbcBackend, JdbcProfile}
import slick.jdbc.PostgresProfile.api._

import java.sql.{Connection, DriverManager}
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.concurrent.duration.Duration

class V2Spec extends BaseSpec with ForAllTestContainer {

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

  lazy val config: Config = ConfigFactory
    .parseResources("migration.conf")
    .withValue("akka.projection.slick.db.url", ConfigValueFactory.fromAnyRef(container.jdbcUrl))
    .withValue("akka.projection.slick.db.user", ConfigValueFactory.fromAnyRef(container.username))
    .withValue("akka.projection.slick.db.password", ConfigValueFactory.fromAnyRef(container.password))
    .withValue("akka.projection.slick.db.serverName", ConfigValueFactory.fromAnyRef(container.host))
    .withValue("akka.projection.slick.db.databaseName", ConfigValueFactory.fromAnyRef(container.databaseName))
    .withValue("write-side-slick.db.url", ConfigValueFactory.fromAnyRef(container.jdbcUrl))
    .withValue("write-side-slick.db.user", ConfigValueFactory.fromAnyRef(container.username))
    .withValue("write-side-slick.db.password", ConfigValueFactory.fromAnyRef(container.password))
    .withValue("write-side-slick.db.serverName", ConfigValueFactory.fromAnyRef(container.host))
    .withValue("write-side-slick.db.databaseName", ConfigValueFactory.fromAnyRef(container.databaseName))
    .resolve()

  lazy val testKit: ActorTestKit = ActorTestKit(config)

  def countLegacyJournal(journalJdbcConfig: DatabaseConfig[JdbcProfile],
                         legacyJournalQueries: legacy.JournalQueries
  ): Int = {
    val q: DBIO[Int] = legacyJournalQueries.JournalTable.map(_.ordering).length.result
    Await.result(journalJdbcConfig.db.run(q), Duration.Inf)
  }

  def countLegacySnapshot(journalJdbcConfig: DatabaseConfig[JdbcProfile], queries: SnapshotQueries): Int = {
    val q: DBIO[Int] = queries.SnapshotTable.length.result
    Await.result(journalJdbcConfig.db.run(q), Duration.Inf)
  }

  def getEventJournalNextSequenceValue(journalConfig: JournalConfig, journaldb: JdbcBackend.Database)(implicit
    ec: ExecutionContext
  ): Long = {
    val schemaName: String = journalConfig.eventJournalTableConfiguration.schemaName.getOrElse("cos")
    val tableName: String = s"$schemaName.event_journal"

    val eventualLong: Future[Long] = for {
      seqName: String <- journaldb.run(
        sql"""SELECT pg_get_serial_sequence($tableName, 'ordering')""".as[String].head
      )
      lastVal <- journaldb.run(sql""" SELECT last_value FROM #$seqName """.as[Long].head)
    } yield lastVal

    Await.result(eventualLong, Duration.Inf)
  }

  override def beforeEach(): Unit = {
    recreateSchema()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    testKit.shutdownTestKit()
  }

  ".beforeUpgrade and upgrade" should {
    "create the new journal table and migrate both snapshot and journal data and cleanup the legacy stores" in {
      implicit val ec: ExecutionContextExecutor = testKit.system.executionContext
      implicit val sys: ActorSystem[Nothing] = testKit.system

      val journalJdbcConfig: DatabaseConfig[JdbcProfile] = JdbcConfig.journalConfig(config)
      val projectionJdbcConfig = JdbcConfig.projectionConfig(config)
      val profile: JdbcProfile = journalJdbcConfig.profile
      val journalConfig: JournalConfig = new JournalConfig(config.getConfig("jdbc-journal"))

      val legacyJournalQueries: legacy.JournalQueries =
        new legacy.JournalQueries(profile, journalConfig.journalTableConfiguration)
      val snapshotConfig: SnapshotConfig = new SnapshotConfig(config.getConfig("jdbc-snapshot-store"))
      val legacySnapshotqueries: SnapshotQueries =
        new SnapshotQueries(profile, snapshotConfig.legacySnapshotTableConfiguration)
      val newSnapshotQueries: dao.SnapshotQueries =
        new dao.SnapshotQueries(profile, snapshotConfig.snapshotTableConfiguration)

      val newJournalQueries: JournalQueries =
        new JournalQueries(profile,
                           journalConfig.eventJournalTableConfiguration,
                           journalConfig.eventTagTableConfiguration
        )

      val serialization: Serialization = SerializationExtension(testKit.system)
      val journaldb: JdbcBackend.Database =
        SlickExtension(testKit.system).database(config.getConfig("jdbc-read-journal")).database

      val snapshotdb: JdbcBackend.Database =
        SlickExtension(testKit.system).database(config.getConfig("jdbc-snapshot-store")).database
      val legacySnapshotDao: ByteArraySnapshotDao =
        new ByteArraySnapshotDao(snapshotdb, profile, snapshotConfig, serialization)

      val v2 = V2(journalJdbcConfig, projectionJdbcConfig)

      // let us create the legacy tables
      SchemasUtil.createLegacyJournalAndSnapshot(journalJdbcConfig) shouldBe {}
      DbUtil.tableExists(journalJdbcConfig, "journal") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "snapshot") shouldBe true

      // let us seed some data into the legacy journal
      noException shouldBe thrownBy(Seed.legacyJournal(serialization, legacyJournalQueries, journalJdbcConfig))
      // let us load some data into the legacy snapshot
      noException shouldBe thrownBy(Seed.legacySnapshot(legacySnapshotDao))

      // let us count the old journal
      countLegacyJournal(journalJdbcConfig, legacyJournalQueries) shouldBe 6
      // let us count the legacy snapshot
      countLegacySnapshot(journalJdbcConfig, legacySnapshotqueries) shouldBe 4

      // let us create the new journal table
      SchemasUtil.createJournalTables(journalJdbcConfig) shouldBe {}
      DbUtil.tableExists(journalJdbcConfig, "event_journal") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "event_tag") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "state_snapshot") shouldBe true

      // let us run beforeUpgrade
      v2.beforeUpgrade().isSuccess shouldBe true

      // let us get the number of records in the new journal
      // let us get the number of records in the new journal
      Await.result(journalJdbcConfig.db
                     .run(newJournalQueries.JournalTable.map(_.ordering).length.result),
                   Duration.Inf
      ) shouldBe countLegacyJournal(journalJdbcConfig, legacyJournalQueries)

      // let us assert the ordering number
      getEventJournalNextSequenceValue(journalConfig, journaldb) shouldBe 7L

      // let us get the number of records in the new snapshot
      Await.result(journalJdbcConfig.db
                     .run(newSnapshotQueries.SnapshotTable.length.result),
                   Duration.Inf
      ) shouldBe countLegacySnapshot(journalJdbcConfig, legacySnapshotqueries)

      // let us run the upgrade
      Await.result(journalJdbcConfig.db.run(v2.upgrade()), Duration.Inf) shouldBe {}

      // let us check the existence of the legacy journal and snapshot
      DbUtil.tableExists(journalJdbcConfig, "journal") shouldBe false
      DbUtil.tableExists(journalJdbcConfig, "snapshot") shouldBe false
    }
  }
}
