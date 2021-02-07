/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.legacy

import akka.actor.ActorSystem
import akka.persistence.jdbc.config.{JournalConfig, ReadJournalConfig, SnapshotConfig}
import akka.persistence.jdbc.db.SlickExtension
import akka.persistence.jdbc.journal.dao.DefaultJournalDao
import akka.persistence.jdbc.query.dao.legacy.ByteArrayReadJournalDao
import akka.persistence.jdbc.snapshot.dao.DefaultSnapshotDao
import akka.persistence.jdbc.snapshot.dao.legacy.ByteArraySnapshotDao
import akka.serialization.SerializationExtension
import com.typesafe.config.Config
import slick.basic.DatabaseConfig
import slick.jdbc.{JdbcBackend, JdbcProfile}

abstract class Migrate(config: Config)(implicit system: ActorSystem) {
  import system.dispatcher

  // let us get the database configuration
  protected val dbconfig = DatabaseConfig.forConfig[JdbcProfile]("write-side-slick", config)
  protected val profile = dbconfig.profile

  // get the various configuration
  protected val journalConfig = new JournalConfig(config.getConfig("jdbc-journal"))
  protected val readJournalConfig = new ReadJournalConfig(config.getConfig("jdbc-read-journal"))

  protected val journaldb: JdbcBackend.Database =
    SlickExtension(system).database(config.getConfig("jdbc-read-journal")).database

  // get an instance of the legacy read journal dao
  protected val legacyReadJournalDao: ByteArrayReadJournalDao =
    new ByteArrayReadJournalDao(journaldb, profile, readJournalConfig, SerializationExtension(system))

  // get an instance of the default journal dao
  protected val defaultJournalDao: DefaultJournalDao =
    new DefaultJournalDao(journaldb, profile, journalConfig, SerializationExtension(system))

  protected val snapshotConfig = new SnapshotConfig(config.getConfig("jdbc-snapshot-store"))

  protected val snapshotdb: JdbcBackend.Database =
    SlickExtension(system).database(config.getConfig("jdbc-snapshot-store")).database

  // get the instance of the legacy snapshot dao
  protected val legacySnapshotDao: ByteArraySnapshotDao =
    new ByteArraySnapshotDao(snapshotdb, profile, snapshotConfig, SerializationExtension(system))

  // get the instance if the default snapshot dao
  protected val defaultSnapshotDao: DefaultSnapshotDao =
    new DefaultSnapshotDao(snapshotdb, profile, snapshotConfig, SerializationExtension(system))

}
