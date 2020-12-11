package com.namely.chiefofstate.config

import com.typesafe.config.Config

/**
 * Snapshot settings
 *
 * @param disableSnapshot disallow the creation of state snapshot
 * @param retentionFrequency how often a snapshot should be created
 * @param retentionNr the number of snapshot to keep in the store
 * @param deleteEventsOnSnapshot removes all old events before snapshotting
 */
final case class SnapshotConfig(
  disableSnapshot: Boolean,
  retentionFrequency: Int,
  retentionNr: Int,
  deleteEventsOnSnapshot: Boolean
) {}

object SnapshotConfig {
  private val disableSnapshotKey: String = "chiefofstate.snapshot-criteria.disable-snapshot"
  private val retentionFrequencyKey: String = "chiefofstate.snapshot-criteria.retention-frequency"
  private val retentionNrKey: String = "chiefofstate.snapshot-criteria.retention-number"
  private val deleteEventsOnSnapshotKey: String = "chiefofstate.snapshot-criteria.delete-events-on-snapshot"

  /**
   * creates a new instance of SnaphotConfig
   * @param config the config object
   * @return the new instance of SnapshotConfig
   */
  def apply(config: Config): SnapshotConfig = {
    SnapshotConfig(
      config.getBoolean(disableSnapshotKey),
      config.getInt(retentionFrequencyKey),
      config.getInt(retentionNrKey),
      config.getBoolean(deleteEventsOnSnapshotKey)
    )
  }
}
