-- creation of the new akka persistence jdbc schemas

-- event_journal table
CREATE TABLE IF NOT EXISTS ${cos:event_journal}
(
    ordering           BIGSERIAL,
    persistence_id     VARCHAR(255)          NOT NULL,
    sequence_number    BIGINT                NOT NULL,
    deleted            BOOLEAN DEFAULT FALSE NOT NULL,

    writer             VARCHAR(255)          NOT NULL,
    write_timestamp    BIGINT,
    adapter_manifest   VARCHAR(255),

    event_ser_id       INTEGER               NOT NULL,
    event_ser_manifest VARCHAR(255)          NOT NULL,
    event_payload      BYTEA                 NOT NULL,

    meta_ser_id        INTEGER,
    meta_ser_manifest  VARCHAR(255),
    meta_payload       BYTEA,

    PRIMARY KEY (persistence_id, sequence_number)
);

CREATE UNIQUE INDEX ${cos:event_journal}_ordering_idx ON ${cos:event_journal} (ordering);

-- event_tag table
CREATE TABLE IF NOT EXISTS ${cos:event_tag}
(
    event_id BIGINT,
    tag      VARCHAR(256),
    PRIMARY KEY (event_id, tag),
    CONSTRAINT fk_${cos:event_journal}
        FOREIGN KEY (event_id)
            REFERENCES ${cos:event_journal} (ordering)
            ON DELETE CASCADE
);

-- state_snapshot table
CREATE TABLE IF NOT EXISTS ${cos:state_snapshot}
(
    persistence_id        VARCHAR(255) NOT NULL,
    sequence_number       BIGINT       NOT NULL,
    created               BIGINT       NOT NULL,

    snapshot_ser_id       INTEGER      NOT NULL,
    snapshot_ser_manifest VARCHAR(255) NOT NULL,
    snapshot_payload      BYTEA        NOT NULL,

    meta_ser_id           INTEGER,
    meta_ser_manifest     VARCHAR(255),
    meta_payload          BYTEA,

    PRIMARY KEY (persistence_id, sequence_number)
);

-- Renaming of the akka projection offset store columns.
ALTER TABLE ${cos:read_side_offsets}
    RENAME COLUMN "PROJECTION_NAME" TO projection_name;

ALTER TABLE ${cos:read_side_offsets}
    RENAME COLUMN "PROJECTION_KEY" TO projection_key;

ALTER TABLE ${cos:read_side_offsets}
    RENAME COLUMN "CURRENT_OFFSET" TO current_offset;

ALTER TABLE ${cos:read_side_offsets}
    RENAME COLUMN "MANIFEST" TO manifest;

ALTER TABLE ${cos:read_side_offsets}
    RENAME COLUMN "MERGEABLE" TO mergeable;

ALTER TABLE ${cos:read_side_offsets}
    RENAME COLUMN "LAST_UPDATED" TO last_updated;

-- Drop the old primary key index
ALTER TABLE ${cos:read_side_offsets}
    DROP CONSTRAINT "PK_PROJECTION_ID";

-- Drop the old index on the table
DROP INDEX IF EXISTS "PROJECTION_NAME_INDEX";

-- recreate the primary key index on the table
ALTER TABLE ${cos:read_side_offsets}
    ADD PRIMARY KEY (projection_name, projection_key);

-- recreate the new index
CREATE INDEX IF NOT EXISTS projection_name_index ON ${cos:read_side_offsets} (projection_name);
