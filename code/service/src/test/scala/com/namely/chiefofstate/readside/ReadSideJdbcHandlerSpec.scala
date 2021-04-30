/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.readside

import com.namely.chiefofstate.helper.BaseSpec
import com.dimafeng.testcontainers.{ ForAllTestContainer, PostgreSQLContainer }
import org.testcontainers.utility.DockerImageName
import java.sql.{ Connection, DriverManager }
import akka.projection.jdbc.JdbcSession
import akka.projection.eventsourced.EventEnvelope
import com.namely.protobuf.chiefofstate.v1.persistence.EventWrapper
import akka.persistence.query.Offset
import com.namely.protobuf.chiefofstate.v1.common.MetaData
import com.google.protobuf.any
import com.google.protobuf.wrappers.StringValue

class ReadSideJdbcHandlerSpec extends BaseSpec with ForAllTestContainer {

  val cosSchema: String = "cos"

  override val container: PostgreSQLContainer = PostgreSQLContainer
    .Def(dockerImageName = DockerImageName.parse("postgres"), urlParams = Map("currentSchema" -> cosSchema))
    .createContainer()

  /**
   * create connection to the container db for test statements
   */
  def getConnection(container: PostgreSQLContainer): Connection = {
    // load the driver
    Class.forName("org.postgresql.Driver")

    DriverManager.getConnection(container.jdbcUrl, container.username, container.password)
  }

  ".process" should {
    "handle success" in {
      // mock read handler that returns success
      val readHandler = mock[ReadSideHandler]

      (readHandler.processEvent _).expects(*, *, *, *).returning(true).once

      val jdbcHandler = new ReadSideJdbcHandler("tag", "processor", readHandler)
      val jdbcSession: JdbcSession = mock[JdbcSession]
      val entityId: String = "entity-1"

      val event = EventWrapper()
        .withMeta(MetaData().withEntityId(entityId))
        .withEvent(any.Any.pack(StringValue("event")))
        .withResultingState(any.Any.pack(StringValue("event")))

      val envelope = EventEnvelope.create[EventWrapper](offset = Offset.sequence(1L), entityId, 2L, event, 3L)
      noException shouldBe thrownBy(jdbcHandler.process(jdbcSession, envelope))
    }
    "handle failure" in {
      // mock read handler that returns success
      val readHandler = mock[ReadSideHandler]

      (readHandler.processEvent _).expects(*, *, *, *).returning(false).once

      val jdbcHandler = new ReadSideJdbcHandler("tag", "processor", readHandler)
      val jdbcSession: JdbcSession = mock[JdbcSession]
      val entityId: String = "entity-1"

      val event = EventWrapper()
        .withMeta(MetaData().withEntityId(entityId))
        .withEvent(any.Any.pack(StringValue("event")))
        .withResultingState(any.Any.pack(StringValue("event")))

      val envelope = EventEnvelope.create[EventWrapper](offset = Offset.sequence(1L), entityId, 2L, event, 3L)

      an[RuntimeException] shouldBe thrownBy(jdbcHandler.process(jdbcSession, envelope))
    }
  }
}
