/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.legacy
import akka.actor.ActorSystem
import akka.persistence.SnapshotMetadata
import akka.persistence.jdbc.snapshot.dao.legacy.{ByteArraySnapshotSerializer, SnapshotQueries}
import akka.persistence.jdbc.snapshot.dao.legacy.SnapshotTables.SnapshotRow
import akka.persistence.jdbc.snapshot.JdbcSnapshotStore
import com.typesafe.config.Config

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 *  migrates the legacy snapshot data onto the new journal schema.
 *
 * @param config the application config
 * @param system the actor system
 */
final case class SnapshotMigrator(config: Config)(implicit system: ActorSystem) extends Migrator(config) {
  import profile.api._
  import system.dispatcher

  private val queries = new SnapshotQueries(profile, snapshotConfig.legacySnapshotTableConfiguration)
  private val serializer: ByteArraySnapshotSerializer =
    new ByteArraySnapshotSerializer(serialization)

  private val snapshotStore: JdbcSnapshotStore = new JdbcSnapshotStore(config)

  private def toSnapshotData(row: SnapshotRow): (SnapshotMetadata, Any) = {
    serializer.deserialize(row) match {
      case Failure(exception) => throw exception
      case Success(value)     => value
    }
  }

  /**
   * write the latest state snapshot into the new snapshot table applying the proper serialization
   */
  def migrate(): Future[Option[Future[Unit]]] = {
    for {
      rows <- snapshotdb
        .run(
          queries.SnapshotTable.sortBy(_.sequenceNumber.desc).result
        )
    } yield rows.headOption
      .map(toSnapshotData)
      .map { case (metadata, value) =>
        snapshotStore
          .saveAsync(metadata, value)
      }
  }
}
