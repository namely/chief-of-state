/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.helper

import com.namely.protobuf.chiefofstate.v1.persistence.{ EventWrapper, StateWrapper }
import com.dimafeng.testcontainers.{ ForAllTestContainer, PostgreSQLContainer }
import java.sql.{ Connection, DriverManager }
import org.checkerframework.checker.units.qual.s

object DbHelper {
  val serializerId = 5001
  val eventManifest = EventWrapper.scalaDescriptor.fullName.split("/").last
  val snapshotManifest = StateWrapper.scalaDescriptor.fullName.split("/").last

  // helper to inesert a fake journal record
  def insertJournal(
      id: String,
      serId: Int = serializerId,
      serManifest: String = eventManifest,
      payload: Array[Byte] = Array.emptyByteArray): String =
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
      decode('${java.util.Base64.getEncoder().encodeToString(payload)}', 'base64')
    )"""

  // helper to insert fake snapshot record
  def insertSnapshot(
      id: String,
      serId: Int = serializerId,
      serManifest: String = snapshotManifest,
      payload: Array[Byte] = Array.emptyByteArray): String = {
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
      decode('${java.util.Base64.getEncoder().encodeToString(payload)}', 'base64')
    )
    """
  }

  /**
   * create connection to the container db for test statements
   */
  def getConnection(container: PostgreSQLContainer): Connection = {
    // load the driver
    Class.forName("org.postgresql.Driver")

    DriverManager.getConnection(container.jdbcUrl, container.username, container.password)
  }

  // drop the COS schema between tests
  def recreateSchema(container: PostgreSQLContainer, schema: String): Unit = {
    val conn = getConnection(container)
    val statement = conn.createStatement()
    statement.addBatch(s"drop schema if exists $schema cascade")
    statement.addBatch(s"create schema $schema")
    statement.executeBatch()
    conn.close()
  }

  // drop the schema for creation testing
  def dropSchema(container: PostgreSQLContainer, schema: String): Unit = {
    val conn = getConnection(container)
    val statement = conn.createStatement()
    statement.addBatch(s"drop schema if exists $schema cascade")
    statement.executeBatch()
    conn.close()
  }

  /**
   * returns true if schema exists (for testing)
   *
   * @param container test container for postgres
   * @param schema schema name
   * @return true if schema exists
   */
  def schemaExists(container: PostgreSQLContainer, schema: String): Boolean = {
    val conn = getConnection(container)
    val statement = conn.createStatement()
    val sql = s"SELECT count(schema_name) FROM information_schema.schemata WHERE schema_name = '$schema';"
    val result = statement.executeQuery(sql)
    require(result.next, "broken resultset")
    val numSchemas = result.getInt(1)
    require(numSchemas < 2, "broken resultset")
    conn.close()
    // return true if found
    numSchemas == 1
  }
}
