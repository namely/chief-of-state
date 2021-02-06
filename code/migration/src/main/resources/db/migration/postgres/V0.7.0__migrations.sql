-- readside offset stores tables
CREATE TABLE IF NOT EXISTS ${cos:read_side_offsets_store}
(
    "PROJECTION_NAME" VARCHAR(255) NOT NULL,
    "PROJECTION_KEY"  VARCHAR(255) NOT NULL,
    "CURRENT_OFFSET"  VARCHAR(255) NOT NULL,
    "MANIFEST"        VARCHAR(4)   NOT NULL,
    "MERGEABLE"       BOOLEAN      NOT NULL,
    "LAST_UPDATED"    BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS "PROJECTION_NAME_INDEX" ON ${cos:read_side_offsets_store} ("PROJECTION_NAME");

ALTER TABLE ${cos:read_side_offsets_store}
    add constraint "PK_PROJECTION_ID" primary key ("PROJECTION_NAME", "PROJECTION_KEY");

-- Renaming of the akka projection offset store columns.
ALTER TABLE ${cos:read_side_offsets_store}
    RENAME COLUMN "PROJECTION_NAME" TO projection_name;

ALTER TABLE ${cos:read_side_offsets_store}
    RENAME COLUMN "PROJECTION_KEY" TO projection_key;

ALTER TABLE ${cos:read_side_offsets_store}
    RENAME COLUMN "CURRENT_OFFSET" TO current_offset;

ALTER TABLE ${cos:read_side_offsets_store}
    RENAME COLUMN "MANIFEST" TO manifest;

ALTER TABLE ${cos:read_side_offsets_store}
    RENAME COLUMN "MERGEABLE" TO mergeable;

ALTER TABLE ${cos:read_side_offsets_store}
    RENAME COLUMN "LAST_UPDATED" TO last_updated;

-- Drop the old primary key index
ALTER TABLE ${cos:read_side_offsets_store}
    DROP CONSTRAINT "PK_PROJECTION_ID";

-- Drop the old index on the table
DROP INDEX IF EXISTS "PROJECTION_NAME_INDEX";

-- recreate the primary key index on the table
ALTER TABLE ${cos:read_side_offsets_store}
    ADD PRIMARY KEY (projection_name, projection_key);

-- recreate the new index
CREATE INDEX IF NOT EXISTS projection_name_index ON ${cos:read_side_offsets_store} (projection_name);
