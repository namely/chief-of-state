/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.readside

import akka.projection.jdbc.JdbcSession
import java.sql.DriverManager
import java.sql.Connection
import akka.japi.function
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ReadSideJdbcSession(jdbcUrl: String, username: String, password: String) extends JdbcSession {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  // defines a simple jdbc connection
  // FIXME, use Hikari and share a single connection for all projections
  // this may mean passing the connection into the constructor, and instantiating
  // a hikari connection once for the app
  lazy val conn: Connection = {

    logger.info(s"creating jdbc connection for $jdbcUrl")

    // load the driver
    Class.forName("org.postgresql.Driver")

    val connection: Connection = DriverManager
      .getConnection(jdbcUrl, username, password)

    connection.setAutoCommit(false)

    connection
  }

  override def withConnection[Result](func: function.Function[Connection, Result]): Result = {
    func(conn)
  }

  override def commit(): Unit = conn.commit()
  override def rollback(): Unit = conn.rollback()
  override def close(): Unit = conn.close()
}
