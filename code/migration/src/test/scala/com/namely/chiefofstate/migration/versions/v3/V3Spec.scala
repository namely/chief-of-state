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

import java.sql.{Connection, DriverManager}
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
    "strip prefixes from journal persistence IDs, snapshots, and tags" in {

      // create the journal/tags tables
      SchemasUtil.createJournalTables(journalJdbcConfig)
      DbUtil.tableExists(journalJdbcConfig, "event_journal") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "event_tag") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "state_snapshot") shouldBe true

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

      statement.addBatch(insertJournal(1, "chiefOfState|1234"))
      statement.addBatch(insertSnapshot("chiefOfState|1234"))
      statement.addBatch(insertTag(1, "chiefofstate3"))

      statement.addBatch(insertJournal(2, "chiefOfState|a|b|c"))
      statement.addBatch(insertSnapshot("chiefOfState|a|b|c"))
      statement.addBatch(insertTag(2, "chiefofstate2"))

      statement.addBatch(insertJournal(3, "chiefOfState|chiefOfState|but-why-did-you-do-this"))
      statement.addBatch(insertSnapshot("chiefOfState|chiefOfState|but-why-did-you-do-this"))
      statement.addBatch(insertTag(3, "chiefofstate1"))

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
        .map(ordering => {
          results.next() shouldBe true
          (results.getLong(1), results.getString(2), results.getString(3), results.getString(4))
        })
        .toSeq

      val expected = Seq(
        (1, "1234", "3", "1234"),
        (2, "a|b|c", "2", "a|b|c"),
        (3, "chiefOfState|but-why-did-you-do-this", "1", "chiefOfState|but-why-did-you-do-this")
      )

      results.next() shouldBe false

      actual should contain theSameElementsAs expected

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
