-- The migration version must always match the COS version released or to be released.
-- This will help for easy data migration making use of flyway

-- persistence tables
CREATE TABLE IF NOT EXISTS ${cos:journal}
(
    ordering        BIGSERIAL,
    persistence_id  VARCHAR(255)               NOT NULL,
    sequence_number BIGINT                     NOT NULL,
    deleted         BOOLEAN      DEFAULT FALSE NOT NULL,
    tags            VARCHAR(255) DEFAULT NULL,
    message         BYTEA                      NOT NULL,
    PRIMARY KEY (persistence_id, sequence_number)
);
CREATE UNIQUE INDEX IF NOT EXISTS ${cos:journal}_ordering_idx ON ${cos:journal} (ordering);

CREATE TABLE IF NOT EXISTS ${cos:snapshot}
(
    persistence_id  VARCHAR(255) NOT NULL,
    sequence_number BIGINT       NOT NULL,
    created         BIGINT       NOT NULL,
    snapshot        BYTEA        NOT NULL,
    PRIMARY KEY (persistence_id, sequence_number)
);

-- readside offset stores tables
CREATE TABLE IF NOT EXISTS ${cos:read_side_offsets_store}
(
    "PROJECTION_NAME" VARCHAR(255) NOT NULL,
    "PROJECTION_KEY"  VARCHAR(255) NOT NULL,
    "CURRENT_OFFSET"  VARCHAR(255) NOT NULL,
    "MANIFEST"        VARCHAR(4)   NOT NULL,
    "MERGEABLE"       BOOLEAN      NOT NULL,
    "LAST_UPDATED"    BIGINT       NOT NULL,
    PRIMARY KEY ("PROJECTION_NAME", "PROJECTION_KEY")
);

CREATE INDEX IF NOT EXISTS "PROJECTION_NAME_INDEX" ON ${cos:read_side_offsets_store} ("PROJECTION_NAME");
