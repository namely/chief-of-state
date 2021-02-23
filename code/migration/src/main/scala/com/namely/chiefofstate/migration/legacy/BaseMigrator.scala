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
import akka.persistence.jdbc.snapshot.dao.legacy.ByteArraySnapshotDao
import akka.persistence.jdbc.snapshot.dao.DefaultSnapshotDao
import akka.serialization.{Serialization, SerializationExtension}
import com.typesafe.config.Config
import slick.basic.DatabaseConfig
import slick.jdbc.{JdbcBackend, JdbcProfile}

import scala.concurrent.ExecutionContextExecutor

/**
 * This class is extended by the various migrators
 * <ul>
 * <li> [[JournalMigrator]]
 * </ul> [[SnapshotMigrator]]
 *
 * @param config the application configuration
 * @param system the actor system
 */
class BaseMigrator(config: Config)(implicit system: ActorSystem) {
  implicit private val ec: ExecutionContextExecutor = system.dispatcher

  // let us get the database configuration
  protected val profile: JdbcProfile = DatabaseConfig.forConfig[JdbcProfile]("write-side-slick", config).profile

  // let us get the akka serialization
  protected val serialization: Serialization = SerializationExtension(system)

  // get the various configuration
  protected val journalConfig: JournalConfig = new JournalConfig(config.getConfig("jdbc-journal"))
  protected val readJournalConfig: ReadJournalConfig = new ReadJournalConfig(config.getConfig("jdbc-read-journal"))
  protected val snapshotConfig = new SnapshotConfig(config.getConfig("jdbc-snapshot-store"))

  // let us get the various databases
  protected val journaldb: JdbcBackend.Database =
    SlickExtension(system).database(config.getConfig("jdbc-read-journal")).database
  protected val snapshotdb: JdbcBackend.Database =
    SlickExtension(system).database(config.getConfig("jdbc-snapshot-store")).database

  // get an instance of the default journal dao
  protected val defaultJournalDao: DefaultJournalDao =
    new DefaultJournalDao(journaldb, profile, journalConfig, serialization)

  // get the instance of the legacy snapshot dao
  protected val legacySnapshotDao: ByteArraySnapshotDao =
    new ByteArraySnapshotDao(snapshotdb, profile, snapshotConfig, serialization)

  // get the instance if the default snapshot dao
  protected val defaultSnapshotDao: DefaultSnapshotDao =
    new DefaultSnapshotDao(snapshotdb, profile, snapshotConfig, serialization)
}
