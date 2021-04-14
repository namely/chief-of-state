/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration

import slick.dbio.DBIO

import scala.util.{ Success, Try }

/**
 * this trait is used to create and migrate the COS db schema. These version
 * numbers should be part of the code and independent of the docker tag.
 */
trait Version {
  // unique, increasing ID for this version
  def versionNumber: Int

  /**
   * implement this method to upgrade the application to this version. This is
   * run in the same db transaction that commits the version number to the
   * database.
   *
   * @return a DBIO that runs this upgrade
   */
  def upgrade(): DBIO[Unit]

  /**
   * implement this method to snapshot this version (run if no prior versions found)
   *
   * @return a DBIO that creates the version snapshot
   */
  def snapshot(): DBIO[Unit] = {
    DBIO.failed(new NotImplementedError(s"cannot snapshot version $versionNumber"))
  }

  /**
   * optional method to run prior to upgrade, which can be used for expensive
   * data operations like rebuilding the journal across many db transactions.
   * this method is not executed as part of the "upgrade" db transaction.
   *
   * @return Success if the method succeeds
   */
  def beforeUpgrade(): Try[Unit] = Success {}

  /**
   * optional method that runs after the upgrade is completed and version number
   * has been persisted to the db. use this method to conduct actions like
   * removing tables that are no longer needed, etc.
   */
  def afterUpgrade(): Try[Unit] = Success {}
}

object Version {

  /**
   * ordering of versions by versionNumber
   */
  object VersionOrdering extends Ordering[Version] {
    def compare(a: Version, b: Version): Int =
      a.versionNumber.compare(b.versionNumber)
  }
}
