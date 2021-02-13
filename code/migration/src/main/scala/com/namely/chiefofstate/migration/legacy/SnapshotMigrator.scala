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
import com.typesafe.config.Config
import slick.jdbc.PostgresProfile.api._
import slickProfile.api._

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

/**
 *  migrates the legacy snapshot data onto the new journal schema.
 *
 * @param config the application config
 * @param system the actor system
 */
final case class SnapshotMigrator(config: Config)(implicit system: ActorSystem) extends Migrator(config) {
  implicit private val ec: ExecutionContextExecutor = system.dispatcher

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
   * write the latest state snapshot into the new snapshot table applying the proper serialization
   */
  def migrate(): Future[Seq[Future[Unit]]] = {
    for {
      rows <- snapshotdb
        .run(
          queries.SnapshotTable.sortBy(_.sequenceNumber.desc).result
        )
    } yield rows
      .map(toSnapshotData)
      .map { case (metadata, value) =>
        defaultSnapshotDao
          .save(metadata, value)
      }
  }

  /**
   * migrate the latest snapshot
   */
  def migrateLatest(): Future[Option[Future[Unit]]] = {
    for {
      rows <- snapshotdb
        .run(
          queries.SnapshotTable.sortBy(_.sequenceNumber.desc).take(1).result
        )
    } yield rows.headOption
      .map(toSnapshotData)
      .map { case (metadata, value) =>
        defaultSnapshotDao
          .save(metadata, value)
      }
  }

  /**
   *  migrate snapshot from within a range
   * @param offset the offset
   * @param limit the number of data to fetch
   */
  def migrate(offset: Int, limit: Int): Future[Seq[Future[Unit]]] = {
    for {
      rows <- snapshotdb
        .run(
          queries.SnapshotTable.sortBy(_.sequenceNumber.desc).drop(offset).take(limit).result
        )
    } yield rows
      .map(toSnapshotData)
      .map { case (metadata, value) =>
        defaultSnapshotDao
          .save(metadata, value)
      }
  }
}
