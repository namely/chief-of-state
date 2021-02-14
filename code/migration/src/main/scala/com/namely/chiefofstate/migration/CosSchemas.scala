/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration

import akka.actor.typed.ActorSystem
import akka.projection.slick.SlickProjection
import akka.Done
import com.github.ghik.silencer.silent
import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.{JdbcProfile, PostgresProfile}

import java.sql.{Connection, Statement}
import scala.concurrent.{ExecutionContextExecutor, Future}

@silent
object CosSchemas {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * returns the event_journal and event_tag tables ddl
   *
   * @return the sql statement
   */
  private def journalTableDDL(): Seq[String] = {
    Seq(
      s"""
        CREATE TABLE IF NOT EXISTS event_journal(
          ordering BIGSERIAL,
          persistence_id VARCHAR(255) NOT NULL,
          sequence_number BIGINT NOT NULL,
          deleted BOOLEAN DEFAULT FALSE NOT NULL,

          writer VARCHAR(255) NOT NULL,
          write_timestamp BIGINT,
          adapter_manifest VARCHAR(255),

          event_ser_id INTEGER NOT NULL,
          event_ser_manifest VARCHAR(255) NOT NULL,
          event_payload BYTEA NOT NULL,

          meta_ser_id INTEGER,
          meta_ser_manifest VARCHAR(255),
          meta_payload BYTEA,

          PRIMARY KEY(persistence_id, sequence_number)
        );""",
      // create index
      s"""CREATE UNIQUE INDEX event_journal_ordering_idx ON event_journal(ordering);""",
      // create the event_tag
      s"""
        CREATE TABLE IF NOT EXISTS event_tag(
            event_id BIGINT,
            tag VARCHAR(256),
            PRIMARY KEY(event_id, tag),
            CONSTRAINT fk_event_journal
              FOREIGN KEY(event_id)
              REFERENCES event_journal(ordering)
              ON DELETE CASCADE
        );
      """
    )
  }

  /**
   * return the snapshot ddl statement
   * @return the sql statement
   */
  private def snapshotTableDDL(): String = {
    s"""
     CREATE TABLE IF NOT EXISTS state_snapshot (
      persistence_id VARCHAR(255) NOT NULL,
      sequence_number BIGINT NOT NULL,
      created BIGINT NOT NULL,

      snapshot_ser_id INTEGER NOT NULL,
      snapshot_ser_manifest VARCHAR(255) NOT NULL,
      snapshot_payload BYTEA NOT NULL,

      meta_ser_id INTEGER,
      meta_ser_manifest VARCHAR(255),
      meta_payload BYTEA,

      PRIMARY KEY(persistence_id, sequence_number)
     )"""
  }

  /**
   * creates the various write-side stores
   *
   * @param config the application config
   */
  def createIfNotExists(config: Config)(implicit system: ActorSystem[_]): Future[Done] = {

    implicit val ec: ExecutionContextExecutor = system.executionContext

    val dbconfig: DatabaseConfig[JdbcProfile] = JdbcConfig.getWriteJdbcConfig(config)
    val readSideJdbcConfig: DatabaseConfig[PostgresProfile] = JdbcConfig.getReadSideJdbcConfig(config)

    // let us get the database connection
    val conn: Connection = dbconfig.db.createSession().conn
    try {
      val stmt: Statement = conn.createStatement()
      try {
        log.info("setting up chieofstate stores....")
        // create the journal table and snapshot journal
        // if DDLs failed, it will raise an SQLException
        journalTableDDL()
          .map(stmt.execute) // create the journal tables
          .map(_ => stmt.execute(snapshotTableDDL())) // create the snapshot table
          .forall(identity)

        // let us create the readside offset store
        SlickProjection
          .createOffsetTableIfNotExists(readSideJdbcConfig)

      } finally {
        stmt.close()
      }
    } finally {
      log.info("chieofstate stores setup. Releasing resources....")
      conn.close()
      dbconfig.db.close()
    }
  }

}
