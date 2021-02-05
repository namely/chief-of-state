ALTER TABLE read_side_offsets
    RENAME COLUMN "PROJECTION_NAME" TO projection_name;

ALTER TABLE read_side_offsets
    RENAME COLUMN "PROJECTION_KEY" TO projection_key;

ALTER TABLE read_side_offsets
    RENAME COLUMN "CURRENT_OFFSET" TO current_offset;

ALTER TABLE read_side_offsets
    RENAME COLUMN "MANIFEST" TO manifest;

ALTER TABLE read_side_offsets
    RENAME COLUMN "MERGEABLE" TO mergeable;

ALTER TABLE read_side_offsets
    RENAME COLUMN "LAST_UPDATED" TO last_updated;

ALTER TABLE table_name
    DROP CONSTRAINT read_side_offsets_pkey;

DROP INDEX IF EXISTS "PROJECTION_NAME_INDEX";

ALTER TABLE read_side_offsets
    ADD PRIMARY KEY (projection_name, projection_key);

CREATE INDEX IF NOT EXISTS projection_name_index ON read_side_offsets (projection_name);
