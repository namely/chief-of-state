package com.namely.chiefofstate.migration.legacy
import akka.actor.ActorSystem
import akka.persistence.jdbc.config.SnapshotConfig
import akka.persistence.jdbc.db.SlickExtension
import akka.persistence.jdbc.snapshot.dao.DefaultSnapshotDao
import akka.persistence.jdbc.snapshot.dao.legacy.ByteArraySnapshotDao
import akka.serialization.SerializationExtension
import com.typesafe.config.Config
import slick.jdbc.JdbcBackend

class MigrateSnapshot(config: Config)(implicit system: ActorSystem) extends Migrate(config) {
  import system.dispatcher

  private val snapshotConfig = new SnapshotConfig(config.getConfig("jdbc-snapshot-store"))

  private val snapshotdb: JdbcBackend.Database =
    SlickExtension(system).database(config.getConfig("jdbc-snapshot-store")).database

  // get the instance of the legacy snapshot dao
  private val legacySnapshotDao: ByteArraySnapshotDao =
    new ByteArraySnapshotDao(snapshotdb, profile, snapshotConfig, SerializationExtension(system))

  // get the instance if the default snapshot dao
  private val defaultSnapshotDao: DefaultSnapshotDao =
    new DefaultSnapshotDao(snapshotdb, profile, snapshotConfig, SerializationExtension(system))

}
