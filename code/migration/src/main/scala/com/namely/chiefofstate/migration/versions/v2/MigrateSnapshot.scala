/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v2

import akka.actor.typed.ActorSystem
import akka.persistence.jdbc.snapshot.dao.legacy.{ByteArraySnapshotSerializer, SnapshotQueries}
import akka.persistence.jdbc.snapshot.dao.legacy.SnapshotTables.SnapshotRow
import akka.persistence.SnapshotMetadata
import slick.jdbc.PostgresProfile.api._
import slickProfile.api._

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.Await
import scala.concurrent.duration.Duration

case class MigrateSnapshot(system: ActorSystem[_]) extends Migrate {
  private val queries = new SnapshotQueries(profile, snapshotConfig.legacySnapshotTableConfiguration)
  private val serializer: ByteArraySnapshotSerializer =
    new ByteArraySnapshotSerializer(serialization)

  private def toSnapshotData(row: SnapshotRow): (SnapshotMetadata, Any) = {
    serializer.deserialize(row) match {
      case Failure(exception) => throw exception
      case Success(value)     => value
    }
  }

  /**
   * Write the state snapshot data into the new snapshot table applying the proper serialization
   */
  def run(): Unit = {
    val future = snapshotdb
      // stream read rows from snapshot table
      .stream(queries.SnapshotTable.result)
      // transform each row async as a future
      .mapResult(toSnapshotData)
      // for each transformed row, write to the destination
      .foreach({
        case (metadata, value) => defaultSnapshotDao.save(metadata, value)
      })

    Await.result(future, Duration.Inf)
  }
}
