/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v1

import com.namely.chiefofstate.migration.{DbUtil, StringImprovements, Version}
import com.namely.chiefofstate.migration.versions.v1.V1.{createTable, insertInto, offsetTableName, tempTable, OffsetRow}
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.dbio.DBIO
import slick.jdbc.{GetResult, JdbcProfile}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try
import com.namely.chiefofstate.migration.versions.v2.SchemasUtil

/**
 * V1 migration only rename the read side offset store table columns name from
 * uppercase to lower case
 *
 * @param projectionJdbcConfig the projection configuration
 */
case class V1(
  journalJdbcConfig: DatabaseConfig[JdbcProfile],
  projectionJdbcConfig: DatabaseConfig[JdbcProfile],
  priorOffsetStoreTable: String
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

  /**
   * implement this method to upgrade the application to this version. This is
   * run in the same db transaction that commits the version number to the
   * database.
   *
   * @return a DBIO that runs this upgrade
   */
  override def upgrade(): DBIO[Unit] = {
    // get the correct table name
    val oldTable: String = transformTableName()
    log.info(s"finalizing ChiefOfState migration: #$versionNumber")
    DBIO.seq(
      // dropping the old table in the new projection connection
      sqlu"""DROP TABLE IF EXISTS #$oldTable CASCADE""",
      // renaming the temporary table in the new projection connection
      sqlu"""ALTER TABLE #$tempTable RENAME TO #$offsetTableName""",
      // creating the required index on the renamed table
      sqlu"""CREATE INDEX #${offsetTableName}_projection_name_index ON #$offsetTableName (projection_name)"""
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

    log.info(s"num records migrating to $tempTable: ${data.size}")

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
    if (priorOffsetStoreTable.isUpper) priorOffsetStoreTable.quote
    else priorOffsetStoreTable
  }
}

object V1 {
  // offset store temporary table name
  private[v1] val tempTable: String = "v1_offset_store_temp"
  // offset store final table name
  private[v1] val offsetTableName: String = "read_side_offsets"

  /**
   * creates the temporary offset store table in the write side database
   *
   * @param journalJdbcConfig the database config
   */
  private[v1] def createTable(journalJdbcConfig: DatabaseConfig[JdbcProfile]): Unit = {
    // TODO: consider a "current schema" object so v1 migration isn't importing something from v2
    val stmt = SchemasUtil.createReadSideOffsetsStmt(tempTable).withPinnedSession.transactionally

    Await.result(journalJdbcConfig.db.run(stmt), Duration.Inf)
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
