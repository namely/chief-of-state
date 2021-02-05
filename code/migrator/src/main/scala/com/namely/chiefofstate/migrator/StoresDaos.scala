/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migrator

import akka.actor.ActorSystem
import akka.persistence.jdbc.db.SlickExtension
import akka.persistence.jdbc.journal.dao.DefaultJournalDao
import akka.persistence.jdbc.query.dao.legacy.ByteArrayReadJournalDao
import akka.persistence.jdbc.snapshot.dao.legacy.ByteArraySnapshotDao
import akka.persistence.jdbc.snapshot.dao.DefaultSnapshotDao
import akka.serialization.SerializationExtension
import slick.basic.DatabaseConfig
import slick.jdbc.{JdbcBackend, JdbcProfile}
import slick.jdbc

final case class StoresDaos(migratorConfig: StoresConfig)(implicit system: ActorSystem) {
  import system.dispatcher

  // get an instance of the database and the Jdbc profile
  val profile: JdbcProfile = DatabaseConfig.forConfig[JdbcProfile]("slick").profile

  val journaldb: JdbcBackend.Database =
    SlickExtension(system).database(migratorConfig.config.getConfig("jdbc-read-journal")).database
  val snapshotdb: jdbc.JdbcBackend.Database =
    SlickExtension(system).database(migratorConfig.config.getConfig("jdbc-snapshot-store")).database

  // get an instance of the legacy read journal dao
  val legacyReadJournalDao: ByteArrayReadJournalDao =
    new ByteArrayReadJournalDao(journaldb, profile, migratorConfig.readJournalConfig, SerializationExtension(system))

  // get an instance of the default journal dao
  val defaultJournalDao: DefaultJournalDao =
    new DefaultJournalDao(journaldb, profile, migratorConfig.journalConfig, SerializationExtension(system))

  // get the instance of the legacy snapshot dao
  val legacySnapshotDao: ByteArraySnapshotDao =
    new ByteArraySnapshotDao(snapshotdb, profile, migratorConfig.snapshotConfig, SerializationExtension(system))

  // get the instance if the default snapshot dao
  val defaultSnapshotDao: DefaultSnapshotDao =
    new DefaultSnapshotDao(snapshotdb, profile, migratorConfig.snapshotConfig, SerializationExtension(system))
}
