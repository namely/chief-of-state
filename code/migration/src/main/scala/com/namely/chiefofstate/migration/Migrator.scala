/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration

import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

import java.time.Instant
import scala.collection.mutable
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.Try
import scala.util.Success

class Migrator(val journalDbConfig: DatabaseConfig[JdbcProfile]) {
  val logger: Logger = Migrator.logger

  // create the priority queue of versions sorted by version number
  private[migration] val versions: mutable.PriorityQueue[Version] = {
    implicit val versionOrdering = Version.VersionOrdering
    new mutable.PriorityQueue()
  }

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
  private[migration] def getVersions(startAt: Int = 0): Seq[Version] = {
    this.versions.clone.filter(_.versionNumber >= startAt).dequeueAll.reverse
  }

  /**
   * runs before all migration steps, used to configure the migrator state, etc.
   */
  private[migration] def beforeAll(): Try[Unit] = {
    // create the versions table
    Migrator
      .createMigrationsTable(journalDbConfig)
      // set initial version if provided
      .flatMap(_ => Migrator.setInitialVersion(journalDbConfig))
  }

  /**
   * run the migration, including setup and all steps
   */
  def run(): Try[Unit] = {
    this
      // run before all step
      .beforeAll()
      // get last version
      .map(_ => Migrator.getCurrentVersionNumber(journalDbConfig))
      // use the version number to upgrade/snapshot
      .flatMap({
        // if no prior version, snapshot using the last one
        case None =>
          val lastVersion: Version = getVersions().last
          Migrator.snapshotVersion(this.journalDbConfig, lastVersion)

        // if there is a prior version, run each subsequent upgrade
        case Some(versionNumber) =>
          // find all subsequent versions available
          val versionsToUpgrade: Seq[Version] = getVersions(versionNumber + 1)

          if(versionsToUpgrade.nonEmpty) {
            logger.info(s"upgrading COS schema, currentVersion: $versionNumber, newerVersions: ${versionsToUpgrade.size}")

            versionsToUpgrade.foldLeft(Try {})((output, version) => {
              output
                .flatMap(_ => {
                  Migrator.upgradeVersion(this.journalDbConfig, version)
                })
            })
          } else {
            logger.info("COS schema is up to date")
            Success {}
          }
      })
  }
}

object Migrator {
  private[migration] val logger: Logger = LoggerFactory.getLogger(getClass)

  val COS_MIGRATIONS_TABLE: String = "cos_migrations"

  val COS_MIGRATIONS_INITIAL_VERSION: String = "COS_MIGRATIONS_INITIAL_VERSION"

  /**
   * run version snapshot and set version in db
   *
   * @param dbConfig a jdbc db config for the journal
   * @param version version to snapshot
   * @return success/failure
   */
  private[migration] def snapshotVersion(dbConfig: DatabaseConfig[JdbcProfile], version: Version): Try[Unit] = {
    logger.info(s"Snapshotting version ${version.versionNumber}")

    val stmt = version
      .snapshot()
      .andThen(setCurrentVersionNumber(dbConfig, version.versionNumber, true))
      .withPinnedSession
      .transactionally

    val future: Future[Int] = dbConfig.db.run(stmt)
    Try(Await.result(future, Duration.Inf))
  }

  /**
   * run version upgrade and set db version
   *
   * @param dbConfig jdbc db config for journal
   * @param version version to upgrade to
   * @return success/failure
   */
  private[migration] def upgradeVersion(dbConfig: DatabaseConfig[JdbcProfile], version: Version): Try[Unit] = {
    logger.info(s"upgrading to version ${version.versionNumber}")
    Try {
      // check prior version number
      val priorVersionNumber: Int = getCurrentVersionNumber(dbConfig).getOrElse(-1)
      require(priorVersionNumber >= 0, "no prior version, cannot upgrade")
      require(
        priorVersionNumber + 1 == version.versionNumber,
        s"cannot upgrade from version $priorVersionNumber to ${version.versionNumber}"
      )
    }
      .flatMap(_ => version.beforeUpgrade())
      // run upgrade
      .flatMap(_ => {
        val stmt = version
          .upgrade()
          .andThen(setCurrentVersionNumber(dbConfig, version.versionNumber, false))
          .withPinnedSession
          .transactionally

        val future: Future[Int] = dbConfig.db.run(stmt)
        Try(Await.result(future, Duration.Inf))
      })
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
     CREATE TABLE IF NOT EXISTS #$COS_MIGRATIONS_TABLE (
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
    val sqlStmt = sql"SELECT version_number from #$COS_MIGRATIONS_TABLE".as[Int]

    val result = Await.result(dbConfig.db.run(sqlStmt), Duration.Inf)

    result match {
      case Seq()    => None
      case versions => Some(versions.max)
    }
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
  ): SqlAction[Int, NoStream, Effect] = {
    val currentTs: Long = Instant.now().toEpochMilli()

    sqlu"""
        INSERT INTO cos_migrations(version_number, upgraded_at, is_snapshot)
        VALUES ($versionNumber, $currentTs, $isSnapshot)
      """
  }

  /**
   * allows user to provide an initial version number via env var
   *
   * @param journalDbConfig the DatabaseConfig to use
   * @return success/failure
   */
  private[migration] def setInitialVersion(journalDbConfig: DatabaseConfig[JdbcProfile]): Try[Unit] = {
    Try {
      // if no prior version number, allow providing via env var
      if (getCurrentVersionNumber(journalDbConfig).isEmpty) {
        // if provided, bootstrap to this version number
        if (sys.env.keySet.contains(COS_MIGRATIONS_INITIAL_VERSION)) {
          val envValue: String = sys.env
            .getOrElse(COS_MIGRATIONS_INITIAL_VERSION, "")
            .trim()

          require(envValue.nonEmpty, s"${COS_MIGRATIONS_INITIAL_VERSION} setting provided empty")
          require(Try(envValue.toInt).isSuccess, s"${COS_MIGRATIONS_INITIAL_VERSION} cannot be '$envValue'")

          val versionNumber: Int = envValue.toInt

          logger.info(s"initializing migration history with version number: $versionNumber")
          val stmt = setCurrentVersionNumber(journalDbConfig, versionNumber, true)
          Await.result(journalDbConfig.db.run(stmt), Duration.Inf)
        }
      }
    }
  }
}
