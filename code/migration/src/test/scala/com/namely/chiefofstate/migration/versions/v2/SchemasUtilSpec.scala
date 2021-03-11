/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v2

import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import com.namely.chiefofstate.migration.{BaseSpec, DbUtil}
import com.namely.chiefofstate.migration.helper.TestConfig
import com.namely.chiefofstate.migration.versions.v1.V1Schema
import org.testcontainers.utility.DockerImageName
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import java.sql.{Connection, DriverManager}
import akka.persistence.jdbc.query.dao.legacy.ReadJournalQueries
import akka.persistence.jdbc.config.ReadJournalConfig
import akka.persistence.jdbc.journal.dao.legacy.{JournalRow => LegacyJournalRow}
import slick.dbio.DBIOAction
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import slick.jdbc.PostgresProfile.api._
import akka.projection.slick.SlickProjection
import akka.actor.testkit.typed.scaladsl.ActorTestKit

class SchemasUtilSpec extends BaseSpec with ForAllTestContainer {
  val testKit: ActorTestKit = ActorTestKit()

  val cosSchema: String = "cos"

  override val container: PostgreSQLContainer = PostgreSQLContainer
    .Def(
      dockerImageName = DockerImageName.parse("postgres"),
      urlParams = Map("currentSchema" -> cosSchema)
    )
    .createContainer()

  // journal jdbc config
  lazy val journalJdbcConfig: DatabaseConfig[JdbcProfile] = TestConfig.dbConfigFromUrl(
    container.jdbcUrl,
    container.username,
    container.password,
    "write-side-slick"
  )

  def runAndWait[R](a: DBIOAction[R, NoStream, Nothing]): R = {
    Await.result(journalJdbcConfig.db.run(a), Duration.Inf)
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

  override def beforeEach(): Unit = {
    recreateSchema()
  }

  "An instance of SchemasUtils" should {
    "create the journal tables" in {
      SchemasUtil.createJournalTables(journalJdbcConfig) shouldBe {}
      DbUtil.tableExists(journalJdbcConfig, "event_journal") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "event_tag") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "state_snapshot") shouldBe true
    }

    " drop the journal tables" in {
      SchemasUtil.createJournalTables(journalJdbcConfig) shouldBe {}
      DbUtil.tableExists(journalJdbcConfig, "event_journal") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "event_tag") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "state_snapshot") shouldBe true

      SchemasUtil.dropJournalTables(journalJdbcConfig) shouldBe {}
      DbUtil.tableExists(journalJdbcConfig, "event_journal") shouldBe false
      DbUtil.tableExists(journalJdbcConfig, "event_tag") shouldBe false
      DbUtil.tableExists(journalJdbcConfig, "state_snapshot") shouldBe false
    }
  }

  ".updateReadOffsets" should {
    "update offsets to the new journal ordering" in {
      val testConn = getConnection
      testConn.setSchema(cosSchema)

      // create old and new journals
      runAndWait(V1Schema.createJournal)
      SchemasUtil.createJournalTables(journalJdbcConfig)

      DbUtil.tableExists(journalJdbcConfig, "event_journal") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "read_side_offsets") shouldBe true

      // insert data with big gaps in sequences
      testConn
        .createStatement()
        .execute(s"""
          INSERT INTO journal(ordering, persistence_id, sequence_number, message) VALUES
          (20, 'id-1', 1, decode('DEADBEEF', 'hex')),
          (30, 'id-1', 2, decode('DEADBEEF', 'hex')),
          (40, 'id-2', 1, decode('DEADBEEF', 'hex')),
          (50, 'id-2', 2, decode('DEADBEEF', 'hex'))
        """)

      // insert events into new journal without gaps
      val manifest: String = "com.namely.protobuf.chiefofstate.v1.persistence.EventWrapper"
      testConn
        .createStatement()
        .execute(s"""
          INSERT INTO event_journal(
            ordering,
            persistence_id,
            sequence_number,
            writer,
            write_timestamp,
            event_ser_id,
            event_ser_manifest,
            event_payload
          ) VALUES
          (1, 'id-1', 1, 'some-writer', 0, 2, '$manifest', decode('DEADBEEF', 'hex')),
          (2, 'id-1', 2, 'some-writer', 1, 2, '$manifest', decode('DEADBEEF', 'hex')),
          (3, 'id-2', 1, 'some-writer', 2, 2, '$manifest', decode('DEADBEEF', 'hex')),
          (4, 'id-2', 2, 'some-writer', 3, 2, '$manifest', decode('DEADBEEF', 'hex'))
        """)

      // insert to offset table (with gaps)
      testConn
        .createStatement()
        .execute(s"""
          INSERT INTO read_side_offsets(
            projection_name,
            projection_key,
            current_offset,
            manifest,
            mergeable,
            last_updated)
          VALUES
            ('some-project', '1', '30', 'SEQ', false, 1),
            ('some-project', '2', '50', 'SEQ', false, 3)
        """)

      // run the update!
      runAndWait(SchemasUtil.updateReadOffsets)

      // read the output
      val results = testConn
        .createStatement()
        .executeQuery("""
          select projection_key, current_offset
          from read_side_offsets
          order by projection_key
        """)

      // first record should have the offset 2
      results.next() shouldBe true
      results.getString(1) shouldBe "1"
      results.getString(2) shouldBe "2"

      // second recod should have the offset 4
      results.next() shouldBe true
      results.getString(1) shouldBe "2"
      results.getString(2) shouldBe "4"

      // there should be no more records
      results.next() shouldBe false

      testConn.close()
    }
  }
}
