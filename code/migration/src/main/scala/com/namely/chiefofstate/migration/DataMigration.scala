package com.namely.chiefofstate.migration

import scala.concurrent.Future

/**
 * This trait will be used by any data migration implementation
 */
trait DataMigration {

  /**
   * the migration version
   */
  val version: String

  def migrate(): Future[Unit]
}
