/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v2

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.persistence.jdbc.config.JournalConfig
import akka.persistence.jdbc.db.SlickExtension
import akka.persistence.jdbc.journal.dao.{legacy, JournalQueries}
import akka.serialization.{Serialization, SerializationExtension}
import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import com.namely.chiefofstate.migration.{BaseSpec, DbUtil, JdbcConfig}
import com.namely.protobuf.chiefofstate.v1.persistence.EventWrapper
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.testcontainers.utility.DockerImageName
import slick.basic.DatabaseConfig
import slick.jdbc.{JdbcBackend, JdbcProfile}
import slick.jdbc.PostgresProfile.api._

import java.sql.{Connection, DriverManager}
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration.Duration

class MigrateJournalSpec extends BaseSpec with ForAllTestContainer {

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
      noException shouldBe thrownBy(Testdata.feedLegacyJournal(serialization, legacyJournalQueries, journalJdbcConfig))

      // let us count the old journal
      countLegacyJournal(journalJdbcConfig, legacyJournalQueries) shouldBe 6

      // let us test the next ordering value
      migrator.nextOrderingValue() shouldBe 7L
    }

    "migrate legacy journal data into the new journal schema" in {

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

      // let us create the legacy tables
      SchemasUtil.createLegacyJournalAndSnapshot(journalJdbcConfig) shouldBe {}
      DbUtil.tableExists(journalJdbcConfig, "journal") shouldBe true

      // let us seed some data into the legacy journal
      noException shouldBe thrownBy(Testdata.feedLegacyJournal(serialization, legacyJournalQueries, journalJdbcConfig))

      // let us count the old journal
      countLegacyJournal(journalJdbcConfig, legacyJournalQueries) shouldBe 6

      // let us test the next ordering value
      migrator.nextOrderingValue() shouldBe 7L

      // let us create the new journal table
      SchemasUtil.createJournalTables(journalJdbcConfig) shouldBe {}
      DbUtil.tableExists(journalJdbcConfig, "event_journal") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "event_tag") shouldBe true

      // let us migrate the data
      migrator.run() shouldBe {}

      // let us get the number of records in the new journal
      Await.result(journalJdbcConfig.db
                     .run(newJournalQueries.JournalTable.map(_.ordering).length.result),
                   Duration.Inf
      ) shouldBe countLegacyJournal(journalJdbcConfig, legacyJournalQueries)
    }

    "migrate legacy journal  data into the new journal schema one by one" in {

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
      val migrator: MigrateJournal = MigrateJournal(testKit.system, profile, serialization, 1)

      // let us create the legacy tables
      SchemasUtil.createLegacyJournalAndSnapshot(journalJdbcConfig) shouldBe {}

      // let us seed some data into the legacy journal
      noException shouldBe thrownBy(Testdata.feedLegacyJournal(serialization, legacyJournalQueries, journalJdbcConfig))

      // let us count the old journal
      countLegacyJournal(journalJdbcConfig, legacyJournalQueries) shouldBe 6

      // let us test the next ordering value
      migrator.nextOrderingValue() shouldBe 7L

      // let us create the new journal table
      SchemasUtil.createJournalTables(journalJdbcConfig) shouldBe {}

      // let us migrate the data
      migrator.run() shouldBe {}

      // let us get the number of records in the new journal
      Await.result(journalJdbcConfig.db
                     .run(newJournalQueries.JournalTable.map(_.ordering).length.result),
                   Duration.Inf
      ) shouldBe countLegacyJournal(journalJdbcConfig, legacyJournalQueries)
    }

    "migrate legacy journal  data into the new journal and check the next ordering number" in {
      implicit val ec: ExecutionContextExecutor = testKit.system.executionContext

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

      // let us create the legacy tables
      SchemasUtil.createLegacyJournalAndSnapshot(journalJdbcConfig) shouldBe {}

      // let us seed some data into the legacy journal
      noException shouldBe thrownBy(Testdata.feedLegacyJournal(serialization, legacyJournalQueries, journalJdbcConfig))

      // let us count the old journal
      countLegacyJournal(journalJdbcConfig, legacyJournalQueries) shouldBe 6

      // let us create the new journal table
      SchemasUtil.createJournalTables(journalJdbcConfig) shouldBe {}

      // let us migrate the data
      migrator.run() shouldBe {}

      // let us get the number of records in the new journal
      // let us get the number of records in the new journal
      Await.result(journalJdbcConfig.db
                     .run(newJournalQueries.JournalTable.map(_.ordering).length.result),
                   Duration.Inf
      ) shouldBe countLegacyJournal(journalJdbcConfig, legacyJournalQueries)

      // let us assert the ordering number
      getEventJournalNextSequenceValue(journalConfig, journaldb) shouldBe 7L
    }

    "migrate legacy journal  data into the new journal and check ordering and sequence number number parity" in {
      implicit val ec: ExecutionContextExecutor = testKit.system.executionContext

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

      // let us create the legacy tables
      SchemasUtil.createLegacyJournalAndSnapshot(journalJdbcConfig) shouldBe {}

      // let us seed some data into the legacy journal
      noException shouldBe thrownBy(Testdata.feedLegacyJournal(serialization, legacyJournalQueries, journalJdbcConfig))

      // let us count the old journal
      countLegacyJournal(journalJdbcConfig, legacyJournalQueries) shouldBe 6

      // let us create the new journal table
      SchemasUtil.createJournalTables(journalJdbcConfig) shouldBe {}

      // let us migrate the data
      migrator.run() shouldBe {}

      // let us get the number of records in the new journal
      Await.result(journalJdbcConfig.db
                     .run(newJournalQueries.JournalTable.map(_.ordering).length.result),
                   Duration.Inf
      ) shouldBe countLegacyJournal(journalJdbcConfig, legacyJournalQueries)

      // let us assert the ordering number
      getEventJournalNextSequenceValue(journalConfig, journaldb) shouldBe 7L

      // let fetch the data from the old journal
      val oldOrdNrAndSeqNr = Await
        .result(
          journalJdbcConfig.db
            .run(legacyJournalQueries.JournalTable.map(journal => (journal.ordering, journal.sequenceNumber)).result),
          Duration.Inf
        )

      val newOrdNrAndSeqNr = Await
        .result(
          journalJdbcConfig.db
            .run(newJournalQueries.JournalTable.map(journal => (journal.ordering, journal.sequenceNumber)).result),
          Duration.Inf
        )

      (oldOrdNrAndSeqNr should contain).theSameElementsInOrderAs(newOrdNrAndSeqNr)
    }

    "migrate legacy journal  data into the new journal and check persisted event manifest" in {
      implicit val ec: ExecutionContextExecutor = testKit.system.executionContext

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

      // let us create the legacy tables
      SchemasUtil.createLegacyJournalAndSnapshot(journalJdbcConfig) shouldBe {}

      // let us seed some data into the legacy journal
      noException shouldBe thrownBy(Testdata.feedLegacyJournal(serialization, legacyJournalQueries, journalJdbcConfig))

      // let us count the old journal
      countLegacyJournal(journalJdbcConfig, legacyJournalQueries) shouldBe 6

      // let us create the new journal table
      SchemasUtil.createJournalTables(journalJdbcConfig) shouldBe {}

      // let us migrate the data
      migrator.run() shouldBe {}

      // let us get the number of records in the new journal
      Await.result(journalJdbcConfig.db
                     .run(newJournalQueries.JournalTable.map(_.ordering).length.result),
                   Duration.Inf
      ) shouldBe countLegacyJournal(journalJdbcConfig, legacyJournalQueries)

      // let us assert the ordering number
      getEventJournalNextSequenceValue(journalConfig, journaldb) shouldBe 7L

      // let us get the manifest
      val manifests: Seq[String] = Await.result(
        journalJdbcConfig.db
          .run(newJournalQueries.JournalTable.map(_.eventSerManifest).result),
        Duration.Inf
      )

      manifests.map(manifest => {
        manifest shouldBe "com.namely.protobuf.chiefofstate.v1.persistence.EventWrapper"
      })
    }

    "migrate legacy journal data into the new journal and validate event persisted" in {
      implicit val ec: ExecutionContextExecutor = testKit.system.executionContext

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

      // let us create the legacy tables
      SchemasUtil.createLegacyJournalAndSnapshot(journalJdbcConfig) shouldBe {}

      // let us seed some data into the legacy journal
      noException shouldBe thrownBy(Testdata.feedLegacyJournal(serialization, legacyJournalQueries, journalJdbcConfig))

      // let us count the old journal
      countLegacyJournal(journalJdbcConfig, legacyJournalQueries) shouldBe 6

      // let us create the new journal table
      SchemasUtil.createJournalTables(journalJdbcConfig) shouldBe {}

      // let us migrate the data
      migrator.run() shouldBe {}

      // let us get the number of records in the new journal
      Await.result(journalJdbcConfig.db
                     .run(newJournalQueries.JournalTable.map(_.ordering).length.result),
                   Duration.Inf
      ) shouldBe countLegacyJournal(journalJdbcConfig, legacyJournalQueries)

      // let us assert the ordering number
      getEventJournalNextSequenceValue(journalConfig, journaldb) shouldBe 7L

      // let us get the manifest
      val payloads: Seq[Array[Byte]] = Await.result(
        journalJdbcConfig.db
          .run(newJournalQueries.JournalTable.map(_.eventPayload).result),
        Duration.Inf
      )

      payloads.map(bytea => {
        EventWrapper.validate(bytea).isSuccess shouldBe true
      })

    }

    "migrate legacy journal data into the new journal and validate tags" in {
      implicit val ec: ExecutionContextExecutor = testKit.system.executionContext

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

      // let us create the legacy tables
      SchemasUtil.createLegacyJournalAndSnapshot(journalJdbcConfig) shouldBe {}

      // let us seed some data into the legacy journal
      noException shouldBe thrownBy(Testdata.feedLegacyJournal(serialization, legacyJournalQueries, journalJdbcConfig))

      // let us count the old journal
      countLegacyJournal(journalJdbcConfig, legacyJournalQueries) shouldBe 6

      // let us create the new journal table
      SchemasUtil.createJournalTables(journalJdbcConfig) shouldBe {}

      // let us migrate the data
      migrator.run() shouldBe {}

      // let us get the number of records in the new journal
      Await.result(journalJdbcConfig.db
                     .run(newJournalQueries.JournalTable.map(_.ordering).length.result),
                   Duration.Inf
      ) shouldBe countLegacyJournal(journalJdbcConfig, legacyJournalQueries)

      // let us assert the ordering number
      getEventJournalNextSequenceValue(journalConfig, journaldb) shouldBe 7L

      // let us get the manifest
      val payloads: Seq[Array[Byte]] = Await.result(
        journalJdbcConfig.db
          .run(newJournalQueries.JournalTable.map(_.eventPayload).result),
        Duration.Inf
      )

      payloads.map(bytea => {
        EventWrapper.validate(bytea).isSuccess shouldBe true
      })

      val legacyTagsAndOrd = Await
        .result(
          journalJdbcConfig.db
            .run(
              legacyJournalQueries.JournalTable
                .sortBy(_.ordering.asc)
                .map(journal => (journal.ordering, journal.tags))
                .result
            ),
          Duration.Inf
        )
        .map({ case (l, maybeString) =>
          (l, maybeString.getOrElse(""))
        })

      val newTagsAndOrd = Await
        .result(
          journalJdbcConfig.db
            .run(
              newJournalQueries.TagTable
                .sortBy(_.eventId.asc)
                .map(t => (t.eventId, t.tag))
                .result
            ),
          Duration.Inf
        )

      (legacyTagsAndOrd should contain).theSameElementsInOrderAs(newTagsAndOrd)
    }

  }
}
