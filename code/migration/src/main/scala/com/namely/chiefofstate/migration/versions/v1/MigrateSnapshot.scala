package com.namely.chiefofstate.migration.versions.v1

import akka.actor.typed.ActorSystem
import akka.persistence.jdbc.snapshot.dao.legacy.{ByteArraySnapshotSerializer, SnapshotQueries}
import akka.persistence.jdbc.snapshot.dao.legacy.SnapshotTables.SnapshotRow
import akka.persistence.SnapshotMetadata
import slick.jdbc.PostgresProfile.api._
import slickProfile.api._

import scala.concurrent.Future
import scala.util.{Failure, Success}

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
  def run(): Future[Seq[Future[Unit]]] = {
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
}
