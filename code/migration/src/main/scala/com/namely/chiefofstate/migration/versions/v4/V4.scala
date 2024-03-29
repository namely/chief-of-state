/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v4

import com.namely.chiefofstate.migration.Version
import com.namely.protobuf.chiefofstate.v1.persistence.{ EventWrapper, StateWrapper }
import org.slf4j.{ Logger, LoggerFactory }
import slick.basic.DatabaseConfig
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

/**
 * V4 updates persistence serialization ID's for the custom serializer
 *
 * @param projectionJdbcConfig the projection configuration
 */
case class V4(journalJdbcConfig: DatabaseConfig[JdbcProfile]) extends Version {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  override def versionNumber: Int = 4

  /**
   * Runs the upgrade, which uses update statements and regex to
   * remove the prefixes from old events
   *
   * @return a DBIO that runs this upgrade
   */
  override def upgrade(): DBIO[Unit] = {
    log.info(s"running upgrade for version #$versionNumber")

    DBIO.seq(
      // update in the journal
      sqlu"""
        UPDATE event_journal
        SET event_ser_id = #${V4.newSerializerId}, event_ser_manifest = ${V4.newSerializerManifestEvent}
        WHERE event_ser_id = #${V4.oldSerializerId} AND event_ser_manifest = ${V4.oldSerializerManifestEvent}
      """,
      // update the snapshots
      sqlu"""
        UPDATE state_snapshot
        SET snapshot_ser_id = #${V4.newSerializerId}, snapshot_ser_manifest = ${V4.newSerializerManifestState}
        WHERE snapshot_ser_id = #${V4.oldSerializerId} AND snapshot_ser_manifest = ${V4.oldSerializerManifestState}
      """)
  }
}

object V4 {
  val oldSerializerId: Int = 2
  val oldSerializerManifestEvent: String = "com.namely.protobuf.chiefofstate.v1.persistence.EventWrapper"
  val oldSerializerManifestState: String = "com.namely.protobuf.chiefofstate.v1.persistence.StateWrapper"
  val newSerializerId: Int = 5001
  val newSerializerManifestEvent: String = EventWrapper.scalaDescriptor.fullName.split("/").last
  val newSerializerManifestState: String = StateWrapper.scalaDescriptor.fullName.split("/").last
}
