/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import akka.actor.testkit.typed.scaladsl.{ ActorTestKit, BehaviorTestKit, TestProbe }
import akka.actor.typed.ActorRef
import com.dimafeng.testcontainers.{ ForAllTestContainer, PostgreSQLContainer }
import com.google.protobuf.wrappers.StringValue
import com.namely.chiefofstate.helper.BaseSpec
import com.namely.chiefofstate.migration.{ JdbcConfig, Migrator }
import com.namely.chiefofstate.serialization.{ MessageWithActorRef, ScalaMessage }
import com.namely.protobuf.chiefofstate.v1.internal.{ MigrationSucceeded, StartMigration }
import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }
import org.testcontainers.utility.DockerImageName
import scalapb.GeneratedMessage

import java.sql.{ Connection, DriverManager, Statement }
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration.{ Duration, FiniteDuration }

class ServiceMigrationRunnerSpec extends BaseSpec with ForAllTestContainer {
  val cosSchema: String = "cos"

  val replyTimeout: FiniteDuration = FiniteDuration(30, TimeUnit.SECONDS)

  override val container: PostgreSQLContainer = PostgreSQLContainer
    .Def(dockerImageName = DockerImageName.parse("postgres:11"), urlParams = Map("currentSchema" -> cosSchema))
    .createContainer()

  def recreateSchema(): Unit = {
    // load the driver
    Class.forName("org.postgresql.Driver")

    val connection: Connection = DriverManager.getConnection(container.jdbcUrl, container.username, container.password)

    val statement: Statement = connection.createStatement()
    statement.addBatch(s"drop schema if exists $cosSchema cascade")
    statement.addBatch(s"create schema $cosSchema")
    statement.executeBatch()
  }

  lazy val config: Config = ConfigFactory
    .parseResources("test.conf")
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

  override def beforeEach(): Unit = {
    super.beforeEach()
    recreateSchema()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    testKit.shutdownTestKit()
  }

  "ServiceMigrationRunner" should {
    "execute the migration request as expected" in {
      // create an instance of ServiceMigrationRunner
      val migrationRunnerRef: ActorRef[ScalaMessage] = testKit.spawn(ServiceMigrationRunner(config))

      // create a message sender and a response receiver
      val probe: TestProbe[GeneratedMessage] = testKit.createTestProbe[GeneratedMessage]()

      // send the migration command to the migrator
      migrationRunnerRef ! MessageWithActorRef(StartMigration.defaultInstance, probe.ref)

      probe.receiveMessage(replyTimeout) match {
        case _: MigrationSucceeded => succeed
        case _                     => fail("unexpected message type")
      }
    }

    "execute the migration request as expected when migration already run" in {
      val dbConfig = JdbcConfig.journalConfig(config)
      // create the versions table
      Migrator.createMigrationsTable(dbConfig).isSuccess shouldBe true

      // set the current version to 5
      val stmt = Migrator.setCurrentVersionNumber(dbConfig, 5, isSnapshot = true)

      Await.ready(dbConfig.db.run(stmt), Duration.Inf)

      // create an instance of ServiceMigrationRunner
      val migrationRunnerRef: ActorRef[ScalaMessage] = testKit.spawn(ServiceMigrationRunner(config))

      // create a message sender and a response receiver
      val probe: TestProbe[GeneratedMessage] = testKit.createTestProbe[GeneratedMessage]()

      // send the migration command to the migrator
      migrationRunnerRef ! MessageWithActorRef(StartMigration.defaultInstance, probe.ref)

      probe.receiveMessage(replyTimeout) match {
        case _: MigrationSucceeded => succeed
        case _                     => fail("unexpected message type")
      }
    }

    "execute the migration request as expected when table exist with migration to run" in {
      val dbConfig = JdbcConfig.journalConfig(config)
      // create the versions table
      Migrator.createMigrationsTable(dbConfig).isSuccess shouldBe true

      // create an instance of ServiceMigrationRunner
      val migrationRunnerRef: ActorRef[ScalaMessage] = testKit.spawn(ServiceMigrationRunner(config))

      // create a message sender and a response receiver
      val probe: TestProbe[GeneratedMessage] = testKit.createTestProbe[GeneratedMessage]()

      // send the migration command to the migrator
      migrationRunnerRef ! MessageWithActorRef(StartMigration.defaultInstance, probe.ref)

      probe.receiveMessage(replyTimeout) match {
        case _: MigrationSucceeded => succeed
        case _                     => fail("unexpected message type")
      }
    }

    "stop because of unhandled scalapb GeneratedMessage" in {
      // create an instance of ServiceMigrationRunner
      val migrationRunnerRef: BehaviorTestKit[ScalaMessage] = BehaviorTestKit(ServiceMigrationRunner(config))

      // create a message sender and a response receiver
      val probe: TestProbe[GeneratedMessage] = testKit.createTestProbe[GeneratedMessage]()

      // send the migration command to the migrator
      migrationRunnerRef.run(MessageWithActorRef(StringValue("x"), probe.ref))

      // no message will be received by the receiving actor
      probe.expectNoMessage()

      migrationRunnerRef.isAlive shouldBe false
    }
  }
}
