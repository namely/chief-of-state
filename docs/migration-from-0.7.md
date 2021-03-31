# Migrating from 0.7 to 0.8+

This document describes the upgrade from COS v0.7 to v0.8. If you are starting
a new application OR are already running on a COS version >= 0.8, you may
ignore these instructions.
### Preparing for migration

The following yaml snippets describes the environment variables that should be
set for the migration.

```yaml
# *******************************
# Initialize the migrator
# *******************************
# COS v0.8 upgrades to the latest akka persistence libraries (>5.0.0),
# which change the journal, snapshot, and tags representations in the
# db. COS v0.8 provides a new built-in migrator to handle this upgrade
# on boot. See Akka docs for more info
# https://doc.akka.io/docs/akka-persistence-jdbc/5.0.0/migration.html
COS_MIGRATIONS_INITIAL_VERSION: 0

# *******************************
# Migrate read side offsets
# *******************************
# Support for a separate read-side offset store is now deprecated. If
# you had provided alternate DB credentials for the read side offsets,
# the COS migrator will automatically copy them to your primary
# data store (the same one with your event journal). If you did not
# configure these environment variables in 0.7, you may safely ignore
# these settings.
COS_READ_SIDE_OFFSET_DB_HOST: localhost
COS_READ_SIDE_OFFSET_DB_PORT: 5432
COS_READ_SIDE_OFFSET_DB_USER: postgres
COS_READ_SIDE_OFFSET_DB_PASSWORD: changeme
COS_READ_SIDE_OFFSET_DB_SCHEMA: public
COS_READ_SIDE_OFFSET_DB: postgres
COS_READ_SIDE_OFFSET_STORE_TABLE: read_side_offsets

# *******************************
# Renamed enviornment variables
# *******************************

# renamed COS_EVENTS_BATCH_THRESHOLD to COS_SNAPSHOT_FREQUENCY
# this env var is optional
COS_SNAPSHOT_FREQUENCY: 100
```

### Running the migration

After setting the above environment variables, please boot a single instance
of COS v0.8 (or higher). (There is a feature request to run migrations safely
in clustered mode, https://github.com/namely/chief-of-state/issues/269).

After the node starts and is able to answer requests, you may scale out again
to the desired number of nodes.

### Configuring telemetry

COS v0.8 moves to [OpenTelemetry](https://opentelemetry.io/) for metrics and
tracing and can now emit metrics to the [OLTP collector](https://github.com/open-telemetry/opentelemetry-collector/blob/main/exporter/README.md).
Please see our [docs](https://github.com/namely/chief-of-state/blob/master/docs/configuration.md#telemetry-configuration) for configuring an OTLP collector.

### After the migration

After the migration completes, you can remove the following deprecated
environment variables.

Variables:
- COS_READ_SIDE_OFFSET_DB_HOST
- COS_READ_SIDE_OFFSET_DB_PORT
- COS_READ_SIDE_OFFSET_DB_USER
- COS_READ_SIDE_OFFSET_DB_PASSWORD
- COS_READ_SIDE_OFFSET_DB_SCHEMA
- COS_READ_SIDE_OFFSET_DB
- COS_READ_SIDE_OFFSET_STORE_TABLE
- COS_DB_CREATE_TABLES
