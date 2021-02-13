-- creation of the new akka persistence jdbc schemas

-- event_journal table
CREATE TABLE IF NOT EXISTS ${cos:journal}
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

CREATE UNIQUE INDEX ${cos:journal}_ordering_idx ON ${cos:journal} (ordering);

-- event_tag table
CREATE TABLE IF NOT EXISTS ${cos:event_tag}
(
    event_id BIGINT,
    tag      VARCHAR(256),
    PRIMARY KEY (event_id, tag),
    CONSTRAINT fk_${cos:journal}
        FOREIGN KEY (event_id)
            REFERENCES ${cos:journal} (ordering)
            ON DELETE CASCADE
);

-- state_snapshot table
CREATE TABLE IF NOT EXISTS ${cos:snapshot}
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

-- cos versions table
CREATE TABLE cos_versions
(
    version           VARCHAR(255) NOT NULL,
    migration_version VARCHAR(255) NOT NULL,
    PRIMARY KEY (version)
);

