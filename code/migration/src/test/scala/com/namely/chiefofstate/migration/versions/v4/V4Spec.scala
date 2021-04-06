/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v4

import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import com.namely.chiefofstate.migration.{BaseSpec, DbUtil, SchemasUtil}
import com.namely.chiefofstate.migration.helper.TestConfig
import org.testcontainers.utility.DockerImageName
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import java.sql.{Connection, DriverManager}
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class V4Spec extends BaseSpec with ForAllTestContainer {

  val cosSchema: String = "cos"

  override val container: PostgreSQLContainer = PostgreSQLContainer
    .Def(
      dockerImageName = DockerImageName.parse("postgres"),
      urlParams = Map("currentSchema" -> cosSchema)
    )
    .createContainer()

  lazy val journalJdbcConfig: DatabaseConfig[JdbcProfile] = TestConfig.dbConfigFromUrl(
    container.jdbcUrl,
    container.username,
    container.password,
    "write-side-slick"
  )

  /**
   * create connection to the container db for test statements
   */
  def getConnection(container: PostgreSQLContainer): Connection = {
    // load the driver
    Class.forName("org.postgresql.Driver")

    DriverManager
      .getConnection(container.jdbcUrl, container.username, container.password)
  }

  // drop the COS schema between tests
  def recreateSchema(container: PostgreSQLContainer): Unit = {
    val statement = getConnection(container).createStatement()
    statement.addBatch(s"drop schema if exists $cosSchema cascade")
    statement.addBatch(s"create schema $cosSchema")
    statement.executeBatch()
  }

  override def beforeEach(): Unit = {
    recreateSchema(container)
  }

  def insertJournal(id: String, serId: Int, serManifest: String): String =
    s"""
    insert into event_journal (
      persistence_id,
      sequence_number,
      deleted,
      writer,
      write_timestamp,
      adapter_manifest,
      event_ser_id,
      event_ser_manifest,
      event_payload
    ) values (
      '$id',
      1,
      false,
      'some-writer',
      0,
      'some-manifest',
      $serId,
      '$serManifest',
      'DEADBEEF'::bytea
    )"""

  def insertSnapshot(id: String, serId: Int, serManifest: String): String = {
    s"""
    insert into state_snapshot (
      persistence_id,
      sequence_number,
      created,
      snapshot_ser_id,
      snapshot_ser_manifest,
      snapshot_payload
    ) values (
      '$id',
      1,
      0,
      $serId,
      '$serManifest',
      'DEADBEEF'::bytea
    )
    """
  }

  ".upgrade" should {
    "update journal/snapshot id and manifest" in {
      // create the journal/tags tables
      SchemasUtil.createJournalTables(journalJdbcConfig)
      DbUtil.tableExists(journalJdbcConfig, "event_journal") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "state_snapshot") shouldBe true

      val testConn = getConnection(container)
      val statement = testConn.createStatement()

      // insert a record to migrate
      val id1: String = UUID.randomUUID().toString()
      statement.addBatch(insertJournal(id1, V4.oldSerializerId, V4.oldSerializerManifestEvent))
      statement.addBatch(insertSnapshot(id1, V4.oldSerializerId, V4.oldSerializerManifestState))

      // insert an already migrated record
      val id2: String = UUID.randomUUID().toString()
      statement.addBatch(insertJournal(id2, V4.newSerializerId, V4.newSerializerManifestEvent))
      statement.addBatch(insertSnapshot(id2, V4.newSerializerId, V4.newSerializerManifestState))

      // insert record to ignore
      val id3: String = UUID.randomUUID().toString()
      val unrelatedId = 99
      val unrelatedManifest = "unrelated"
      statement.addBatch(insertJournal(id3, unrelatedId, unrelatedManifest))
      statement.addBatch(insertSnapshot(id3, unrelatedId, unrelatedManifest))

      statement.executeBatch().toSeq.forall(_ >= 0) shouldBe true

      // run the upgrade
      val v4 = V4(journalJdbcConfig)
      val future = journalJdbcConfig.db.run(v4.upgrade())
      Await.result(future, Duration.Inf)

      // create map of UUID -> (serId, serManifest)
      val actualJournal: Map[String, (Int, String)] = {
        val resultStmt = testConn.createStatement()

        val results = resultStmt.executeQuery("""
          select distinct persistence_id, event_ser_id, event_ser_manifest
          from event_journal
          order by persistence_id
        """)

        val output = (1 to 3)
          .map(ordering => {
            results.next() shouldBe true
            (results.getString(1), (results.getInt(2), results.getString(3)))
          })
          .toMap

        results.next() shouldBe false

        output
      }

      // create map of UUID -> (serId, serManifest)
      val actualSnapshot: Map[String, (Int, String)] = {
        val resultStmt = testConn.createStatement()

        val results = resultStmt.executeQuery("""
          select distinct persistence_id, snapshot_ser_id, snapshot_ser_manifest
          from state_snapshot
          order by persistence_id
        """)

        val output = (1 to 3)
          .map(ordering => {
            results.next() shouldBe true
            (results.getString(1), (results.getInt(2), results.getString(3)))
          })
          .toMap

        results.next() shouldBe false

        output
      }

      // assert record 1 was migrated
      actualJournal(id1) shouldBe ((V4.newSerializerId, V4.newSerializerManifestEvent))
      actualSnapshot(id1) shouldBe ((V4.newSerializerId, V4.newSerializerManifestState))

      // assert record 2 remains OK
      actualJournal(id2) shouldBe ((V4.newSerializerId, V4.newSerializerManifestEvent))
      actualSnapshot(id2) shouldBe ((V4.newSerializerId, V4.newSerializerManifestState))

      // assert record 3 was ignored
      actualJournal(id3) shouldBe ((unrelatedId, unrelatedManifest))
      actualSnapshot(id3) shouldBe ((unrelatedId, unrelatedManifest))

      testConn.close()
    }
  }

  ".snapshot" should {
    "create the new journal, snapshot and read side store" in {
      val v4 = V4(journalJdbcConfig)
      Await.result(journalJdbcConfig.db.run(v4.snapshot()), Duration.Inf) shouldBe {}
      DbUtil.tableExists(journalJdbcConfig, "event_journal") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "event_tag") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "state_snapshot") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "read_side_offsets") shouldBe true
    }
  }

}
