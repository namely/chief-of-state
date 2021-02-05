package com.namely.chiefofstate.migrator

import akka.actor.ActorSystem
import akka.persistence.jdbc.db.{SlickDatabase, SlickExtension}
import akka.persistence.jdbc.journal.dao.DefaultJournalDao
import akka.persistence.jdbc.query.dao.legacy.ByteArrayReadJournalDao
import akka.persistence.jdbc.snapshot.dao.legacy.ByteArraySnapshotDao
import akka.persistence.jdbc.snapshot.dao.DefaultSnapshotDao
import akka.serialization.SerializationExtension

final case class MigratorDaos(migratorConfig: MigratorConfig)(implicit system: ActorSystem) {
  import system.dispatcher

  // get an instance of the database
  private val slickdb: SlickDatabase =
    SlickExtension(system).database(migratorConfig.config.getConfig("write-side-slick"))

  // get an instance of the legacy read journal dao
  val legacyReadJournalDao: ByteArrayReadJournalDao =
    new ByteArrayReadJournalDao(slickdb.database,
                                slickdb.profile,
                                migratorConfig.readJournalConfig,
                                SerializationExtension(system)
    )

  // get an instance of the default journal dao
  val defaultJournalDao =
    new DefaultJournalDao(slickdb.database,
                          slickdb.profile,
                          migratorConfig.journalConfig,
                          SerializationExtension(system)
    )

  // get the instance of the legacy snapshot dao
  val legacySnapshotDao =
    new ByteArraySnapshotDao(slickdb.database,
                             slickdb.profile,
                             migratorConfig.snapshotConfig,
                             SerializationExtension(system)
    )

  // get the instance if the default snapshot dao
  val defaultSnapshotDao =
    new DefaultSnapshotDao(slickdb.database,
                           slickdb.profile,
                           migratorConfig.snapshotConfig,
                           SerializationExtension(system)
    )
}
