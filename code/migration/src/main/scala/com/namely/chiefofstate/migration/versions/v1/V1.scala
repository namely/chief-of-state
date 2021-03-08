/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v1

import com.namely.chiefofstate.migration.{DbUtil, Migrator, StringImprovements, Version}
import com.namely.chiefofstate.migration.versions.v1.V1.{createTable, insertInto, tempTable, OffsetRow}
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.dbio.DBIO
import slick.jdbc.{GetResult, JdbcProfile, PostgresProfile}
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

/**
 * V1 migration only rename the read side offset store table columns name from
 * uppercase to lower case
 *
 * @param projectionJdbcConfig the projection configuration
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
      // let us first drop the temporary table if exist
      DbUtil.dropTableIfExists(tempTable, journalJdbcConfig)

      log.info("creating new ChiefOfState read side offset store temporary table")
      createTable(journalJdbcConfig)

      log.info("moving the data accross read side store databases")
      migrate()
    }
  }

  override def afterUpgrade(): Try[Unit] = {
    // get the correct table name
    val table: String = transformTableName()
    Try {
      if (!DbUtil.tableExists(projectionJdbcConfig, Migrator.COS_MIGRATIONS_TABLE)) {
        log.info(s"dropping the read side offset store from the old database connection")
        DbUtil.dropTableIfExists(table, projectionJdbcConfig)
      }
      log.info(s"ChiefOfState migration: #$versionNumber completed")
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
    // get the correct table name
    val table: String = transformTableName()
    log.info(s"finalizing ChiefOfState migration: #$versionNumber")
    DBIO.seq(
      // dropping the old table in the new projection connection
      sqlu"""DROP TABLE IF EXISTS #$table""",
      // renaming the temporary table in the new projection connection
      sqlu"""ALTER TABLE IF EXISTS #$tempTable RENAME TO #$table""",
      // dropping the temporary table index
      sqlu"""DROP INDEX IF EXISTS #${tempTable}_index""",
      // creating the required index on the renamed table
      sqlu"""CREATE INDEX IF NOT EXISTS projection_name_index ON #$table (projection_name)"""
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
    // let us fetch the records
    val data: Seq[OffsetRow] = fetchOffsetRows()

    // let us insert the data into the temporary table
    insertInto(tempTable, journalJdbcConfig, data)
  }

  /**
   * fetches all records in the old offset store table
   */
  private def fetchOffsetRows(): Seq[OffsetRow] = {
    val table: String = transformTableName()
    // read all the data from the old read side offset store
    val query: DBIOAction[Vector[OffsetRow], Streaming[OffsetRow], Effect] =
      sql"""
            SELECT
                c."PROJECTION_NAME",
                c."PROJECTION_KEY",
                c."CURRENT_OFFSET",
                c."MANIFEST",
                c."MERGEABLE",
                c."LAST_UPDATED"
            FROM #$table as c
        """
        .as[OffsetRow]
        .withPinnedSession

    Await.result(projectionJdbcConfig.db.run(query), Duration.Inf)
  }

  /**
   * returns the read side offset store correct table name that
   * can be used sql statement.
   */
  private def transformTableName(): String = {
    if (offsetStoreTable.isUpper) offsetStoreTable.quote
    else offsetStoreTable
  }
}

object V1 {
  // offset store temporary table name
  private[v1] val tempTable: String = "v1_offset_store_temp"

  /**
   * creates the temporary offset store table in the write side database
   *
   * @param journalJdbcConfig the database config
   */
  private[v1] def createTable(journalJdbcConfig: DatabaseConfig[JdbcProfile]): Unit = {
    val tableCreationStmt: SqlAction[Int, NoStream, Effect] =
      sqlu"""
        CREATE TABLE IF NOT EXISTS #$tempTable(
          projection_name VARCHAR(255) NOT NULL,
          projection_key VARCHAR(255) NOT NULL,
          current_offset VARCHAR(255) NOT NULL,
          manifest VARCHAR(4) NOT NULL,
          mergeable BOOLEAN NOT NULL,
          last_updated BIGINT NOT NULL,
          PRIMARY KEY(projection_name, projection_key)
        )"""

    val indexCreationStmt: SqlAction[Int, NoStream, Effect] =
      sqlu"""CREATE INDEX IF NOT EXISTS #${tempTable}_index ON #$tempTable (projection_name)"""

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
    // let us build the insert statement
    val combined: DBIO[Seq[Int]] = DBIO
      .sequence(
        data
          .map(row => {
            sqlu"""
                INSERT INTO #$tableName
                VALUES (${row.projectionName}, ${row.projectionKey}, ${row.currentOffset}, ${row.manifest}, ${row.mergeable}, ${row.lastUpdated})
            """
          })
      )
      .withPinnedSession
      .transactionally

    Await.result(dbConfig.db.run(combined), Duration.Inf).sum
  }

  private[v1] case class OffsetRow(
    projectionName: String,
    projectionKey: String,
    currentOffset: String,
    manifest: String,
    mergeable: Boolean,
    lastUpdated: Long
  )

  implicit val getOffSetRowResult: AnyRef with GetResult[OffsetRow] =
    GetResult(r => OffsetRow(r.<<, r.<<, r.<<, r.<<, r.<<, r.<<))
}
