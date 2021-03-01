/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v1

import akka.actor
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.persistence.jdbc.config.{JournalConfig, ReadJournalConfig, SnapshotConfig}
import akka.persistence.jdbc.db.SlickExtension
import akka.persistence.jdbc.journal.dao.DefaultJournalDao
import akka.persistence.jdbc.snapshot.dao.legacy.ByteArraySnapshotDao
import akka.persistence.jdbc.snapshot.dao.DefaultSnapshotDao
import akka.serialization.{Serialization, SerializationExtension}
import com.namely.chiefofstate.migration.JdbcConfig
import slick.jdbc.{JdbcBackend, JdbcProfile}

import scala.concurrent.ExecutionContextExecutor

trait Migrate {
  // the actor system
  val system: ActorSystem[_]

  implicit val ec: ExecutionContextExecutor = system.executionContext
  implicit val classicSys: actor.ActorSystem = system.toClassic

  // the Jdbc profile
  val profile: JdbcProfile = JdbcConfig.journalJdbcProfile(system.settings.config)

  // let us get the akka serialization
  val serialization: Serialization = SerializationExtension(system)

  // the journal, read journal and snapshot config
  val journalConfig: JournalConfig = new JournalConfig(system.settings.config.getConfig("jdbc-journal"))
  val readJournalConfig: ReadJournalConfig = new ReadJournalConfig(
    system.settings.config.getConfig("jdbc-read-journal")
  )
  val snapshotConfig: SnapshotConfig = new SnapshotConfig(system.settings.config.getConfig("jdbc-snapshot-store"))

  // the various databases
  val journaldb: JdbcBackend.Database =
    SlickExtension(system).database(system.settings.config.getConfig("jdbc-read-journal")).database
  val snapshotdb: JdbcBackend.Database =
    SlickExtension(system).database(system.settings.config.getConfig("jdbc-snapshot-store")).database

  // get an instance of the default journal dao
  val defaultJournalDao: DefaultJournalDao =
    new DefaultJournalDao(journaldb, profile, journalConfig, serialization)

  // get the instance of the legacy snapshot dao
  val legacySnapshotDao: ByteArraySnapshotDao =
    new ByteArraySnapshotDao(snapshotdb, profile, snapshotConfig, serialization)

  // get the instance if the default snapshot dao
  val defaultSnapshotDao: DefaultSnapshotDao =
    new DefaultSnapshotDao(snapshotdb, profile, snapshotConfig, serialization)
}
