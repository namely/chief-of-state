/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v3

import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import com.namely.chiefofstate.migration.{BaseSpec, DbUtil, SchemasUtil}
import com.namely.chiefofstate.migration.helper.TestConfig
import org.testcontainers.utility.DockerImageName
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import java.sql.{Connection, DriverManager, ResultSet, Statement}
import java.util.concurrent.ExecutionException
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class V3Spec extends BaseSpec with ForAllTestContainer {

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

  ".upgrade" should {
    "strip prefixes from journal persistence IDs, snapshots, readside offsets and tags" in {

      // create the journal/tags, snapshot and read_side_offsets tables
      SchemasUtil.createStoreTables(journalJdbcConfig)
      DbUtil.tableExists(journalJdbcConfig, "event_journal") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "event_tag") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "state_snapshot") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "read_side_offsets") shouldBe true

      val testConn = getConnection(container)
      val statement = getConnection(container).createStatement()

      def insertJournal(ordering: Int, id: String): String =
        s"""
          insert into event_journal (
            ordering, persistence_id, sequence_number, deleted, writer, write_timestamp,
            adapter_manifest, event_ser_id, event_ser_manifest, event_payload
          ) values (
            $ordering, '$id', 1, false, 'some-writer', 0,
            'some-manifest', 2, 'some-ser-manifest', 'DEADBEEF'::bytea
          )"""

      def insertSnapshot(id: String): String =
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
          2,
          'some-manifest',
          'DEADBEEF'::bytea
        )
        """

      def insertTag(id: Int, tag: String): String =
        s"""insert into event_tag (event_id, tag) values ($id, '$tag')"""

      def insertOffsets(key: String, offset: String, time: Long): String = {
        s"""
            insert into read_side_offsets(
              projection_name,
              projection_key,
              current_offset,
              manifest,
              mergeable,
              last_updated
            ) values (
              'LOGGER_READSIDE',
              '$key',
              '$offset',
              'SEQ',
              false,
              $time
            )
        """
      }

      val id1: String = "chiefOfState|1234"

      statement.addBatch(insertJournal(1, id1))
      statement.addBatch(insertSnapshot(id1))
      statement.addBatch(insertTag(1, "chiefofstate3"))
      statement.addBatch(insertOffsets("chiefofstate0", "19697", 1617676050105L))

      val id2: String = "chiefOfState|a|b|c"

      statement.addBatch(insertJournal(2, id2))
      statement.addBatch(insertSnapshot(id2))
      statement.addBatch(insertTag(2, "chiefofstate2"))
      statement.addBatch(insertOffsets("chiefofstate1", "19695", 1617675137185L))

      val id3: String = "chiefOfState|chiefOfState|but-why-did-you-do-this"

      statement.addBatch(insertJournal(3, id3))
      statement.addBatch(insertSnapshot(id3))
      statement.addBatch(insertTag(3, "chiefofstate1"))
      statement.addBatch(insertOffsets("chiefofstate3", "19711", 1617682341675L))

      statement.executeBatch()

      // run the upgrade
      val v3 = V3(journalJdbcConfig)
      val future = journalJdbcConfig.db.run(v3.upgrade())
      Await.result(future, Duration.Inf)

      val resultStmt = testConn.createStatement()
      val results = resultStmt.executeQuery("""
        select j.ordering, j.persistence_id, t.tag, s.persistence_id
        from event_journal j
        inner join event_tag t on j.ordering = t.event_id
        inner join state_snapshot s on j.persistence_id = s.persistence_id
        order by j.ordering
      """)

      val actual = (1 to 3)
        .map(_ => {
          results.next() shouldBe true
          (results.getLong(1), results.getString(2), results.getString(3), results.getString(4))
        })

      val expected = Seq(
        (1, "1234", "3", "1234"),
        (2, "a|b|c", "2", "a|b|c"),
        (3, "chiefOfState|but-why-did-you-do-this", "1", "chiefOfState|but-why-did-you-do-this")
      )

      results.next() shouldBe false

      actual should contain theSameElementsAs expected

      // assert the read side offsets
      val stmt: Statement = testConn.createStatement()
      // let us query the read_side_offsets
      val sql: String = s"""select * from read_side_offsets"""
      val resultSet: ResultSet = stmt.executeQuery(sql)

      // we only insert three rows so we can safely loop through them
      val actualReadSideRows =
        (1 to 3).map(_ => {
          resultSet.next() shouldBe true
          (resultSet.getString("projection_name"),
           resultSet.getString("projection_key"),
           resultSet.getString("current_offset"),
           resultSet.getString("manifest"),
           resultSet.getString("mergeable"),
           resultSet.getString("last_updated")
          )
        })

      val expectedReadSideRows = Seq(
        ("LOGGER_READSIDE", "0", "19697", "SEQ", "f", "1617676050105"),
        ("LOGGER_READSIDE", "1", "19695", "SEQ", "f", "1617675137185"),
        ("LOGGER_READSIDE", "3", "19711", "SEQ", "f", "1617682341675")
      )
      resultSet.next() shouldBe false

      actualReadSideRows should contain theSameElementsAs expectedReadSideRows

      testConn.close()
    }
  }

  ".snapshot" should {
    "fail" in {
      val v3 = V3(journalJdbcConfig)
      val err: ExecutionException = intercept[ExecutionException] {
        Await.result(journalJdbcConfig.db.run(v3.snapshot()), Duration.Inf)
      }
      err.getCause().isInstanceOf[NotImplementedError] shouldBe true
    }
  }
}
