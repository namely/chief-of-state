package com.namely.chiefofstate
import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import java.sql.{Connection, Statement}

/**
 * Help create the journal and snapshot stores
 */
case class JournalAndSnapshotMigration(config: Config) {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  private val dc: DatabaseConfig[PostgresProfile] =
    DatabaseConfig.forConfig[PostgresProfile]("write-side-slick", config)
  private val journalTableName: String = config.getString("jdbc-read-journal.tables.journal.tableName")
  private val snapshotTableName: String = config.getString("jdbc-snapshot-store.tables.snapshot.tableName")

  private def createJournalTableStatement(table: String): Seq[String] = {
    Seq(
      s"""
     CREATE TABLE IF NOT EXISTS $table (
      ordering        BIGSERIAL,
      persistence_id  VARCHAR(255) NOT NULL,
      sequence_number BIGINT       NOT NULL,
      deleted         BOOLEAN      DEFAULT FALSE,
      tags            VARCHAR(255) DEFAULT NULL,
      message         BYTEA        NOT NULL,
      PRIMARY KEY (persistence_id, sequence_number)
     )""",
      // create index
      s"""CREATE UNIQUE INDEX IF NOT EXISTS journal_ordering_idx on $table (ordering)"""
    )
  }

  private def createSnapshotTableStatement(table: String): String =
    s"""
     CREATE TABLE IF NOT EXISTS $table (
      persistence_id  VARCHAR(255) NOT NULL,
      sequence_number BIGINT       NOT NULL,
      created         BIGINT       NOT NULL,
      snapshot        BYTEA        NOT NULL,
      PRIMARY KEY (persistence_id, sequence_number)
     )"""

  /**
   *  Attempts to create the various write side data stores
   */
  def createSchemas(): Boolean = {
    val journalSQLs: Seq[String] = createJournalTableStatement(journalTableName)
    val snapshotSQL: String = createSnapshotTableStatement(snapshotTableName)

    val conn: Connection = dc.db.createSession().conn

    try {
      val stmt: Statement = conn.createStatement()
      try {
        log.info("setting up journal and snapshot stores....")
        // create the journal table and snapshot journal
        // if DDLs failed, it will raise an SQLException
        journalSQLs
          .map(stmt.execute)
          .map(_ => stmt.execute(snapshotSQL))
          .forall(identity)

      } finally {
        stmt.close()
      }
    } finally {
      log.info("journal and snapshot stores setup. Releasing resources....")
      conn.close()
      dc.db.close()
    }
  }
}
