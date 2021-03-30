/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v3

import com.namely.chiefofstate.migration.{BaseSpec, DbUtil, JdbcConfig}
import com.namely.chiefofstate.migration.helper.TestConfig
import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import org.testcontainers.utility.DockerImageName
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import java.sql.{DriverManager, Connection}

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
    "strip prefixes from journal persistence IDs and tags" in {

      // create the journal/tags tables
      SchemasUtil.createJournalTables(journalJdbcConfig)
      DbUtil.tableExists(journalJdbcConfig, "event_journal") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "event_tag") shouldBe true

      val testConn = getConnection(container)
      val statement = getConnection(container).createStatement()

      def eventTemplate(ordering: Int, id: String): String =
        s"""( $ordering, '$id', 1, false, 'some-writer', 0,
          'some-manifest', 2, 'some-ser-manifest', 'DEADBEEF'::bytea )"""

      statement.addBatch(s"""
        insert into event_journal
        ( ordering, persistence_id, sequence_number, deleted, writer, write_timestamp,
          adapter_manifest, event_ser_id, event_ser_manifest, event_payload)
        values
        ${eventTemplate(1, "chiefOfState|1234")},
        ${eventTemplate(2, "chiefOfState|a|b|c")},
        ${eventTemplate(3, "chiefOfState|chiefOfState|but-why-did-you-do-this")}
      """)

      statement.addBatch(s"""
        insert into event_tag (event_id, tag) values
        (1, 'chiefofstate3'),
        (2, 'chiefofstate2'),
        (3, 'chiefofstate1')
      """)

      statement.executeBatch()

      // run the upgrade
      val v3 = V3(journalJdbcConfig)
      val future = journalJdbcConfig.db.run(v3.upgrade())
      Await.result(future, Duration.Inf)

      val resultStmt = testConn.createStatement()
      val results = resultStmt.executeQuery("""
        select j.ordering, j.persistence_id, t.tag
        from event_journal j
        inner join event_tag t on j.ordering = t.event_id
        order by j.ordering
      """)

      val actual = (1 to 3)
        .map(ordering => {
          results.next() shouldBe true
          (results.getLong(1), results.getString(2), results.getString(3))
        })
        .toSeq

      val expected = Seq(
        (1, "1234", "3"),
        (2, "a|b|c", "2"),
        (3, "chiefOfState|but-why-did-you-do-this", "1")
      )

      results.next() shouldBe false

      actual should contain theSameElementsAs expected

      testConn.close()
    }
  }

  ".snapshot" should {
    "create the new journal, snapshot and read side store" in {
      val v3 = V3(journalJdbcConfig)
      Await.result(journalJdbcConfig.db.run(v3.snapshot()), Duration.Inf) shouldBe {}
      DbUtil.tableExists(journalJdbcConfig, "event_journal") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "event_tag") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "state_snapshot") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "read_side_offsets") shouldBe true
    }
  }

}
