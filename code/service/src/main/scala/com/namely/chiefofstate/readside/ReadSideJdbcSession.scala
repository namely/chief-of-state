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

/**
 * Simple implementation of a JdbcSession that uses an existing
 * connection. This is meant to be used in a connection pool
 *
 * @param conn a java sql Connection
 */
class ReadSideJdbcSession(val conn: Connection) extends JdbcSession {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def withConnection[Result](func: function.Function[Connection, Result]): Result = {
    func(conn)
  }

  override def commit(): Unit = conn.commit()
  override def rollback(): Unit = conn.rollback()
  override def close(): Unit = conn.close()
}
