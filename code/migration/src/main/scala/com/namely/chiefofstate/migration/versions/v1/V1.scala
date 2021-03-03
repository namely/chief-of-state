/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v1

import com.namely.chiefofstate.migration.{DbUtil, Migrator, Version}
import com.namely.chiefofstate.migration.versions.v1.V1.{insertInto, OFFSET_STORE_TEMP_TABLE, OffsetRow}
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.dbio.DBIO
import slick.jdbc.{GetResult, JdbcProfile, PostgresProfile}
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlStreamingAction

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

/**
 * V1 migration only rename the read side offset store table columns name from
 * uppercase to lower case
 *
 * @param projectionJdbcConfig the projection configuration
 * @param system the actor system
 */
case class V1(
  journalJdbcConfig: DatabaseConfig[JdbcProfile],
  projectionJdbcConfig: DatabaseConfig[JdbcProfile],
  offsetStoreTable: String
) extends Version {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  override def versionNumber: Int = 1

  /**
   * creates a temporary offset store table in the write side database
   *
   *  @return Success if the method succeeds
   */
  override def beforeUpgrade(): Try[Unit] = {
    Try {
      log.info("creating new ChiefOfState read side offset store temporary table")
      V1.createTempTable(journalJdbcConfig)

      log.info("moving the data accross read side store databases")
      migrate()
    }
  }

  override def afterUpgrade(): Try[Unit] = {
    Try {
      if (!DbUtil.tableExists(projectionJdbcConfig, Migrator.COS_MIGRATIONS_TABLE)) {
        log.info(s"dropping the read side offset store from the old database connection")
        val dropStmt: DBIOAction[Int, NoStream, Effect with Effect.Transactional] =
          sqlu"""DROP TABLE IF EXISTS #$offsetStoreTable""".withPinnedSession.transactionally
        Await.result(projectionJdbcConfig.db.run(dropStmt), Duration.Inf)
        log.info(s"ChiefOfState migration: #$versionNumber completed")
      }
    }
  }

  /**
   * implement this method to upgrade the application to this version. This is
   * run in the same db transaction that commits the version number to the
   * database.
   *
   * @return a DBIO that runs this upgrade
   */
  override def upgrade(): DBIO[Unit] = {
    log.info(s"finalizing ChiefOfState migration: #$versionNumber")
    DBIO.seq(
      // dropping the old table in the new projection connection
      sqlu"""DROP TABLE IF EXISTS #$offsetStoreTable""",
      // renaming the temporary table in the new projection connection
      sqlu"""ALTER TABLE IF EXISTS #$OFFSET_STORE_TEMP_TABLE RENAME TO #$offsetStoreTable""",
      // dropping the temporary table index
      sqlu"""DROP INDEX IF EXISTS #${OFFSET_STORE_TEMP_TABLE}_index""",
      // creating the required index on the renamed table
      sqlu"""CREATE INDEX IF NOT EXISTS projection_name_index ON #$offsetStoreTable (projection_name)"""
    )
  }

  /**
   * implement this method to snapshot this version (run if no prior versions found)
   *
   * @return a DBIO that creates the version snapshot
   */
  override def snapshot(): DBIO[Unit] = DBIO.failed(new RuntimeException("snaphotting not allowed in this version"))

  /**
   * move the data from the old read side offset store table to the new temporay offset store table by
   * reading all records into memory.
   *
   * @return the number of the record inserted into the table
   */
  private def migrate(): Int = {
    // read all the data from the old read side offset store
    val sqlStmt: SqlStreamingAction[Vector[OffsetRow], OffsetRow, Effect] =
      sql"""
            SELECT
                c."PROJECTION_NAME",
                c."PROJECTION_KEY",
                c."CURRENT_OFFSET",
                c."MANIFEST",
                c."MERGEABLE",
                c."LAST_UPDATED"
            FROM #$offsetStoreTable as c
        """.as[OffsetRow]

    // let us fetch the data
    val data: Seq[OffsetRow] = Await.result(projectionJdbcConfig.db.run(sqlStmt), Duration.Inf)

    // let us insert the data into the temporary table
    insertInto(OFFSET_STORE_TEMP_TABLE, journalJdbcConfig, data)
  }
}

object V1 {
  // offset store temporary table name
  private[v1] val OFFSET_STORE_TEMP_TABLE: String = "v1_offset_store_temp"

  /**
   * creates the temporary offset store table in the write side database
   *
   * @param journalJdbcConfig the database config
   */
  private[v1] def createTempTable(journalJdbcConfig: DatabaseConfig[JdbcProfile]): Unit = {
    val tableCreationStmt =
      sqlu"""
        CREATE TABLE IF NOT EXISTS #$OFFSET_STORE_TEMP_TABLE(
          projection_name VARCHAR(255) NOT NULL,
          projection_key VARCHAR(255) NOT NULL,
          current_offset VARCHAR(255) NOT NULL,
          manifest VARCHAR(4) NOT NULL,
          mergeable BOOLEAN NOT NULL,
          last_updated BIGINT NOT NULL,
          PRIMARY KEY(projection_name, projection_key)
        )"""

    val indexCreationStmt =
      sqlu"""CREATE INDEX IF NOT EXISTS #${OFFSET_STORE_TEMP_TABLE}_index ON #$OFFSET_STORE_TEMP_TABLE (projection_name)"""

    val ddlSeq: DBIOAction[Unit, NoStream, PostgresProfile.api.Effect with Effect.Transactional] = DBIO
      .seq(
        tableCreationStmt,
        indexCreationStmt
      )
      .withPinnedSession
      .transactionally

    Await.result(journalJdbcConfig.db.run(ddlSeq), Duration.Inf)
  }

  /**
   * Insert a set of offset store record into the offset store table
   *
   * @param tableName the table name
   * @param dbConfig the db config
   * @param data the data to insert
   * @return the number of record inserted
   */
  private[v1] def insertInto(tableName: String, dbConfig: DatabaseConfig[JdbcProfile], data: Seq[OffsetRow]): Int = {
    def insertToStmt(offsetRow: OffsetRow, tableName: String): DBIO[Int] = {
      sqlu"INSERT INTO #$tableName VALUES (${offsetRow.projectionName}, ${offsetRow.projectionKey}, ${offsetRow.offsetStr}, ${offsetRow.manifest}, ${offsetRow.mergeable}, ${offsetRow.lastUpdated})"
    }

    val combined: DBIO[Seq[Int]] = DBIO.sequence(data.map(row => {
      insertToStmt(row, tableName)
    }))

    Await.result(dbConfig.db.run(combined), Duration.Inf).sum
  }

  private[v1] case class OffsetRow(
    projectionName: String,
    projectionKey: String,
    offsetStr: String,
    manifest: String,
    mergeable: Boolean,
    lastUpdated: Long
  )

  implicit val getOffSetRowResult: AnyRef with GetResult[OffsetRow] =
    GetResult(r => OffsetRow(r.<<, r.<<, r.<<, r.<<, r.<<, r.<<))
}
