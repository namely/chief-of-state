/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v2

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.persistence.jdbc.config.JournalConfig
import akka.persistence.jdbc.db.SlickExtension
import akka.persistence.jdbc.journal.dao.{legacy, JournalQueries}
import akka.persistence.jdbc.journal.dao.legacy.ByteArrayJournalDao
import akka.serialization.{Serialization, SerializationExtension}
import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.{ExposedPort, PortBinding, Ports}
import com.namely.chiefofstate.migration.{BaseSpec, JdbcConfig}
import com.namely.chiefofstate.migration.versions.v2.Seeding.insertNonSerializedDataIntoLegacy
import com.typesafe.config.{Config, ConfigFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.{JdbcBackend, JdbcProfile}
import slick.jdbc.PostgresProfile.api._

import java.sql.{Connection, DriverManager}
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration.Duration

class MigrateJournalSpec extends BaseSpec with ForAllTestContainer {

  val cosSchema: String = "cos"
  val containerExposedPort: Int = 5432
  val hostPort: Int = 8003

  override val container: PostgreSQLContainer = PostgreSQLContainer().configure { c =>
    c.withDatabaseName("test")
    c.withUsername("test")
    c.withPassword("changeme")
    c.withExposedPorts(containerExposedPort)
    c.withUrlParam("currentSchema", cosSchema)
    c.withExtraHost("localhost", "0.0.0.0")
    c.withCreateContainerCmdModifier((t: CreateContainerCmd) => {
      t.withPortBindings(new PortBinding(Ports.Binding.bindPort(hostPort), new ExposedPort(containerExposedPort)))
    })
  }

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
    .parseResources("v2-migration.conf")
    .resolve()

  lazy val testKit: ActorTestKit = ActorTestKit(config)

  def countLegacyJournal(journalJdbcConfig: DatabaseConfig[JdbcProfile],
                         legacyJournalQueries: legacy.JournalQueries
  ): Int = {
    val q: DBIO[Int] = legacyJournalQueries.JournalTable.map(_.ordering).length.result
    Await.result(journalJdbcConfig.db.run(q), Duration.Inf)
  }

  def getEventJournalNextSequenceValue(journalConfig: JournalConfig, journaldb: JdbcBackend.Database)(implicit
    executionContextExecutor: ExecutionContextExecutor
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

  "MigrateJournal" should {
    "create legacy tables and insert data onto the new journal" in {
      val journalJdbcConfig: DatabaseConfig[JdbcProfile] = JdbcConfig.journalConfig(config)
      val profile: JdbcProfile = journalJdbcConfig.profile
      val journalConfig: JournalConfig = new JournalConfig(config.getConfig("jdbc-journal"))
      val legacyJournalQueries: legacy.JournalQueries =
        new legacy.JournalQueries(profile, journalConfig.journalTableConfiguration)
      val serialization: Serialization = SerializationExtension(testKit.system)
      val migrator: MigrateJournal = MigrateJournal(testKit.system, profile, serialization)

      // let us create the legacy tables
      SchemasUtil.createLegacyJournalAndSnapshot(journalJdbcConfig) shouldBe {}

      // insert some data in the old journal
      insertNonSerializedDataIntoLegacy(journalJdbcConfig, legacyJournalQueries) shouldBe 8

      // let us count the old journal
      countLegacyJournal(journalJdbcConfig, legacyJournalQueries) shouldBe 8

      // let us test the next ordering value
      migrator.nextOrderingValue() shouldBe 9L
    }

    "migrate legacy journal data into the new journal schema" in {
      implicit val ec: ExecutionContextExecutor = testKit.system.executionContext
      implicit val sys: ActorSystem[Nothing] = testKit.system

      val journalJdbcConfig: DatabaseConfig[JdbcProfile] = JdbcConfig.journalConfig(config)
      val profile: JdbcProfile = journalJdbcConfig.profile
      val journalConfig: JournalConfig = new JournalConfig(config.getConfig("jdbc-journal"))
      val legacyJournalQueries: legacy.JournalQueries =
        new legacy.JournalQueries(profile, journalConfig.journalTableConfiguration)

      val newJournalQueries: JournalQueries =
        new JournalQueries(profile,
                           journalConfig.eventJournalTableConfiguration,
                           journalConfig.eventTagTableConfiguration
        )

      val serialization: Serialization = SerializationExtension(testKit.system)
      val migrator: MigrateJournal = MigrateJournal(testKit.system, profile, serialization)

      val journaldb: JdbcBackend.Database =
        SlickExtension(testKit.system).database(config.getConfig("jdbc-read-journal")).database

      val legacyDao: ByteArrayJournalDao = new ByteArrayJournalDao(journaldb, profile, journalConfig, serialization)

      // let us create the legacy tables
      SchemasUtil.createLegacyJournalAndSnapshot(journalJdbcConfig) shouldBe {}

      // let us seed some data into the legacy journal
      noException shouldBe thrownBy(Seeding.seedLegacyJournal(legacyDao))

      // let us count the old journal
      countLegacyJournal(journalJdbcConfig, legacyJournalQueries) shouldBe 6

      // let us test the next ordering value
      migrator.nextOrderingValue() shouldBe 7L

      // let us create the new journal table
      SchemasUtil.createJournalTables(journalJdbcConfig) shouldBe {}

      // let us migrate the data
      migrator.migrateWithBatchSize() shouldBe {}

      // let us get the number of records in the new journal
      Await.result(journalJdbcConfig.db
                     .run(newJournalQueries.JournalTable.map(_.ordering).length.result),
                   Duration.Inf
      ) shouldBe countLegacyJournal(journalJdbcConfig, legacyJournalQueries)
    }

    "migrate legacy journal  data into the new journal schema one by one" in {
      implicit val ec: ExecutionContextExecutor = testKit.system.executionContext
      implicit val sys: ActorSystem[Nothing] = testKit.system

      val journalJdbcConfig: DatabaseConfig[JdbcProfile] = JdbcConfig.journalConfig(config)
      val profile: JdbcProfile = journalJdbcConfig.profile
      val journalConfig: JournalConfig = new JournalConfig(config.getConfig("jdbc-journal"))
      val legacyJournalQueries: legacy.JournalQueries =
        new legacy.JournalQueries(profile, journalConfig.journalTableConfiguration)

      val newJournalQueries: JournalQueries =
        new JournalQueries(profile,
                           journalConfig.eventJournalTableConfiguration,
                           journalConfig.eventTagTableConfiguration
        )

      val serialization: Serialization = SerializationExtension(testKit.system)
      val migrator: MigrateJournal = MigrateJournal(testKit.system, profile, serialization)

      val journaldb: JdbcBackend.Database =
        SlickExtension(testKit.system).database(config.getConfig("jdbc-read-journal")).database

      val legacyDao: ByteArrayJournalDao = new ByteArrayJournalDao(journaldb, profile, journalConfig, serialization)

      // let us create the legacy tables
      SchemasUtil.createLegacyJournalAndSnapshot(journalJdbcConfig) shouldBe {}

      // let us seed some data into the legacy journal
      noException shouldBe thrownBy(Seeding.seedLegacyJournal(legacyDao))

      // let us count the old journal
      countLegacyJournal(journalJdbcConfig, legacyJournalQueries) shouldBe 6

      // let us test the next ordering value
      migrator.nextOrderingValue() shouldBe 7L

      // let us create the new journal table
      SchemasUtil.createJournalTables(journalJdbcConfig) shouldBe {}

      // let us migrate the data
      migrator.migrateWithBatchSize(1) shouldBe {}

      // let us get the number of records in the new journal
      Await.result(journalJdbcConfig.db
                     .run(newJournalQueries.JournalTable.map(_.ordering).length.result),
                   Duration.Inf
      ) shouldBe countLegacyJournal(journalJdbcConfig, legacyJournalQueries)
    }

    "migrate legacy journal  data into the new journal and check the next ordering number" in {
      implicit val ec: ExecutionContextExecutor = testKit.system.executionContext
      implicit val sys: ActorSystem[Nothing] = testKit.system

      val journalJdbcConfig: DatabaseConfig[JdbcProfile] = JdbcConfig.journalConfig(config)
      val profile: JdbcProfile = journalJdbcConfig.profile
      val journalConfig: JournalConfig = new JournalConfig(config.getConfig("jdbc-journal"))
      val legacyJournalQueries: legacy.JournalQueries =
        new legacy.JournalQueries(profile, journalConfig.journalTableConfiguration)

      val newJournalQueries: JournalQueries =
        new JournalQueries(profile,
                           journalConfig.eventJournalTableConfiguration,
                           journalConfig.eventTagTableConfiguration
        )

      val serialization: Serialization = SerializationExtension(testKit.system)
      val migrator: MigrateJournal = MigrateJournal(testKit.system, profile, serialization)

      val journaldb: JdbcBackend.Database =
        SlickExtension(testKit.system).database(config.getConfig("jdbc-read-journal")).database

      val legacyDao: ByteArrayJournalDao = new ByteArrayJournalDao(journaldb, profile, journalConfig, serialization)

      // let us create the legacy tables
      SchemasUtil.createLegacyJournalAndSnapshot(journalJdbcConfig) shouldBe {}

      // let us seed some data into the legacy journal
      noException shouldBe thrownBy(Seeding.seedLegacyJournal(legacyDao))

      // let us count the old journal
      countLegacyJournal(journalJdbcConfig, legacyJournalQueries) shouldBe 6

      // let us create the new journal table
      SchemasUtil.createJournalTables(journalJdbcConfig) shouldBe {}

      // let us migrate the data
      migrator.migrateWithBatchSize() shouldBe {}

      // let us get the number of records in the new journal
      // let us get the number of records in the new journal
      Await.result(journalJdbcConfig.db
                     .run(newJournalQueries.JournalTable.map(_.ordering).length.result),
                   Duration.Inf
      ) shouldBe countLegacyJournal(journalJdbcConfig, legacyJournalQueries)

      // let us assert the ordering number
      getEventJournalNextSequenceValue(journalConfig, journaldb) shouldBe 7L
    }
  }
}
