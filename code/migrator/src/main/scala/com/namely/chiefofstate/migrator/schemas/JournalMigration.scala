package com.namely.chiefofstate.migrator.schemas

import slick.migration.api.SqlMigration

/**
 * creates the migration SQL statement of the Journal based upon given journal table name
 * and the tag table name
 *
 * @param journalTableName the journal table name
 * @param tagTableName     the tag table name
 */
case class JournalMigration(journalTableName: String, tagTableName: String) extends SqlMigration {
  override def sql: Seq[String] = Seq(
    s"""
        CREATE TABLE IF NOT EXISTS $journalTableName(
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
    s"""CREATE UNIQUE INDEX ${journalTableName}_ordering_idx ON $journalTableName(ordering);""",
    // create the event tag table
    s"""
        CREATE TABLE IF NOT EXISTS $tagTableName(
            event_id BIGINT,
            tag VARCHAR(256),
            PRIMARY KEY(event_id, tag),
            CONSTRAINT fk_$journalTableName
              FOREIGN KEY(event_id)
              REFERENCES $journalTableName(ordering)
              ON DELETE CASCADE
        );
      """
  )
}
