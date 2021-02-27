/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration

import scala.collection.mutable
import scala.util.Success
import org.slf4j.{Logger, LoggerFactory}
import scala.util.Failure
import com.typesafe.config.Config
import slick.jdbc.{JdbcProfile, PostgresProfile}
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction
import java.time.Instant
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import scala.util.{Failure, Success, Try}
import scala.collection.mutable.PriorityQueue

class Migrator(val config: Config) {
  import Migrator.{createMigrationsTable, getCurrentVersionNumber, logger, setCurrentVersionNumber}

  // create the priority queue of versions sorted by version number
  private[migration] val versions: mutable.PriorityQueue[Version] = {
    implicit val versionOrdering = Version.VersionOrdering
    new mutable.PriorityQueue()
  }

  // lazily create the journal db connection
  private[migration] lazy val journalJdbcConfig: DatabaseConfig[JdbcProfile] =
    JdbcConfig.journalConfig(config)

  /**
   * add one version to the runner and return the runner
   *
   * @param version the Version to add
   * @return the runner with that Version added
   */
  def addVersion(version: Version): Migrator = {
    this.versions.addOne(version)
    logger.debug(s"added version ${version.versionNumber}")
    this
  }

  /**
   * get the versions back in order
   *
   * @param startAt inclusive lowest version number, defaults to 0
   * @return sequence of versions in ascending order
   */
  def getVersions(startAt: Int = 0): Seq[Version] = {
    this.versions.clone.filter(_.versionNumber >= startAt).dequeueAll.reverse
  }

  // get a version by ID
  def getVersion(versionNumber): Option[Version] = {

  }

  /**
   * runs before all migration steps, used to configure the migrator state, etc.
   */
  private[migration] def beforeAll(): Try[Unit] = {

    // create the versions table
    createMigrationsTable(journalJdbcConfig)
    // TODO: figure out if the migraiton has been run before and insert a version number
    // to represent the version before this tool existed
  }

  /**
   * run the migration, including setup and all steps
   */
  def run(): Try[Unit] = {

    logger.info(s"starting migrator")

    this
      // run before all step
      .beforeAll()
      // get last version
      .map(_ => getCurrentVersionNumber(journalJdbcConfig))
      // use the version number to upgrade/snapshot
      .flatMap({
        // if no prior version, snapshot using the last one
        case None =>
          val lastVersion: Version = getVersions().last
          Migrator.upgradeVersion(this.journalJdbcConfig, lastVersion)

        // if there is a prior version, run each subsequent upgrade
        case Some(versionNumber) =>
          // find all subsequent versions available
          val versionsToUpgrade: Seq[Version] = getVersions(versionNumber + 1)

          logger.info(s"current version: ${versionNumber}")
          logger.info(s"newer versions found: ${versionsToUpgrade.size}")

          versionsToUpgrade.foldLeft(Try {})((output, version) => {
            output
              .flatMap(_ => {
                logger.info(s"upgrading to version ${version.versionNumber}")
                Migrator.upgradeVersion(this.journalJdbcConfig, version)
              })
          })
      })
  }
}

object Migrator {
  private[migration] val logger: Logger = LoggerFactory.getLogger(getClass)

  /**
   * run version snapshot and set version in db
   *
   * @param dbConfig a jdbc db config for the journal
   * @param version version to snapshot
   * @return success/failure
   */
  private[migration] def snapshotVersion(dbConfig: DatabaseConfig[JdbcProfile], version: Version): Try[Unit] = {
    // TODO: make these 1 db transaction
    version
      .snapshot()
      .flatMap(_ => setCurrentVersionNumber(dbConfig, version.versionNumber, true))
  }

  /**
   * run version upgrade and set db version
   *
   * @param dbConfig jdbc db config for journal
   * @param version version to upgrade to
   * @return success/failure
   */
  private[migration] def upgradeVersion(dbConfig: DatabaseConfig[JdbcProfile], version: Version): Try[Unit] = {
    version
      .beforeUpgrade()
      // TODO: make "upgrade()" and "setCurrentVersion()" run in one db transaction
      .flatMap(_ => version.upgrade())
      .flatMap(_ => setCurrentVersionNumber(dbConfig, version.versionNumber, false))
      // finally conduct afterUpgrade
      .flatMap(_ => version.afterUpgrade())
  }

  /**
   * create the versions table if it doesn't exist
   *
   * @param dbConfig a jdbc db config for the journal
   * @return success/failure
   */
  private[migration] def createMigrationsTable(dbConfig: DatabaseConfig[JdbcProfile]): Try[Unit] = {

    val stmt = sqlu"""
     CREATE TABLE IF NOT EXISTS cos_migrations (
      version_number int primary key not null,
      upgraded_at bigint,
      is_snapshot boolean
     )""".withPinnedSession.transactionally

    Try {
      Await.result(dbConfig.db.run(stmt), Duration.Inf)
    }
  }

  /**
   * get the current version number from the db
   *
   * @param dbConfig a jdbc db config for the journal
   * @return optional version number as an int
   */
  private[migration] def getCurrentVersionNumber(dbConfig: DatabaseConfig[JdbcProfile]): Option[Int] = {
    val sqlStmt = sql"""SELECT max(version_number) from cos_migrations"""
      .as[Int]
      .headOption

    Await.result(dbConfig.db.run(sqlStmt), Duration.Inf)
  }

  /**
   * set the current version number in the db
   *
   * @param dbConfig a jdbc db config for the journal
   * @param versionNumber the version number to reflect in the db
   * @param isSnapshot true if the version was set via a snapshot
   * @return success/failure for db operation (blocking)
   */
  private[migration] def setCurrentVersionNumber(dbConfig: DatabaseConfig[JdbcProfile],
                                                 versionNumber: Int,
                                                 isSnapshot: Boolean
  ): Try[Unit] = {
    val currentTs: Long = Instant.now().toEpochMilli()

    val sqlStmt: DBIOAction[Int, NoStream, Effect] =
      sqlu"""
        INSERT INTO cos_migrations(version_number, upgraded_at, is_snapshot)
        VALUES ($versionNumber, $currentTs, $isSnapshot)
      """.withPinnedSession

    Try(Await.result(dbConfig.db.run(sqlStmt), Duration.Inf))
  }
}
