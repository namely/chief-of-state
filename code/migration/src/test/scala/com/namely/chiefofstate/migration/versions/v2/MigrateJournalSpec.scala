/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v2

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.persistence.jdbc.config.JournalConfig
import akka.persistence.jdbc.journal.dao.legacy
import akka.persistence.jdbc.journal.dao.legacy.JournalRow
import akka.serialization.{Serialization, SerializationExtension}
import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.{ExposedPort, PortBinding, Ports}
import com.namely.chiefofstate.migration.{BaseSpec, JdbcConfig}
import com.namely.protobuf.chiefofstate.v1.tests.{AccountDebited, AccountOpened}
import com.typesafe.config.{Config, ConfigFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

import java.sql.{Connection, DriverManager}
import scala.concurrent.Await
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

  def insertNonSerializedDataIntoLegacy(
    journalJdbcConfig: DatabaseConfig[JdbcProfile],
    legacyJournalQueries: legacy.JournalQueries
  ): Long = {

    val data: Seq[JournalRow] = Seq(
      JournalRow(
        1L,
        false,
        "some-persistence-id-1",
        2L,
        AccountOpened()
          .withAccountNumber("number-1")
          .withAccountUuid("some-persistence-id-1")
          .toByteArray,
        Some("chiefofstate0")
      ),
      JournalRow(
        2L,
        false,
        "some-persistence-id-2",
        4L,
        AccountOpened()
          .withAccountNumber("number-2")
          .withAccountUuid("some-persistence-id-2")
          .toByteArray,
        Some("chiefofstate1")
      ),
      JournalRow(
        3L,
        false,
        "some-persistence-id-3",
        12L,
        AccountOpened()
          .withAccountNumber("number-3")
          .withAccountUuid("some-persistence-id-3")
          .toByteArray,
        Some("chiefofstate0")
      ),
      JournalRow(
        4L,
        false,
        "some-persistence-id-0",
        6L,
        AccountOpened()
          .withAccountNumber("number-0")
          .withAccountUuid("some-persistence-id-0")
          .toByteArray,
        Some("chiefofstate0")
      ),
      JournalRow(
        5L,
        false,
        "some-persistence-id-0",
        8L,
        AccountDebited()
          .withBalance(10)
          .withAccountUuid("some-persistence-id-0")
          .toByteArray,
        Some("chiefofstate2")
      ),
      JournalRow(
        6L,
        false,
        "some-persistence-id-0",
        20L,
        AccountDebited()
          .withBalance(20)
          .withAccountUuid("some-persistence-id-0")
          .toByteArray,
        Some("chiefofstate2")
      ),
      JournalRow(
        7L,
        false,
        "some-persistence-id-3",
        2L,
        AccountDebited()
          .withBalance(20)
          .withAccountUuid("some-persistence-id-3")
          .toByteArray,
        Some("chiefofstate3")
      ),
      JournalRow(
        8L,
        false,
        "some-persistence-id-2",
        2L,
        AccountDebited()
          .withBalance(20)
          .withAccountUuid("some-persistence-id-2")
          .toByteArray,
        Some("chiefofstate0")
      )
    )

    val inserts = legacyJournalQueries.JournalTable
      .returning(legacyJournalQueries.JournalTable.map(_.ordering)) ++= data

    Await.result(journalJdbcConfig.db.run(inserts), Duration.Inf).length
  }

  def countLegacyJournal(journalJdbcConfig: DatabaseConfig[JdbcProfile],
                         legacyJournalQueries: legacy.JournalQueries
  ): Int = {
    val q: DBIO[Int] = legacyJournalQueries.JournalTable.map(_.ordering).length.result
    Await.result(journalJdbcConfig.db.run(q), Duration.Inf)
  }

  override def beforeEach(): Unit = {
    recreateSchema()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    testKit.shutdownTestKit()
  }

  "With MigrateJournal we" should {
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
    "migrate old data into the new journal schema" in {
      // TODO
    }
  }
}
