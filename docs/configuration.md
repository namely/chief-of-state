# Configuration options

This section describes the environment variables for configuration.

See the following deployment-specific guides for relevant configurations:

- [Docker Deployment](./docker-deployment.md)
- [Kubernetes Deployment](./kubernetes-deployment.md)

### Global environment variables

| environment variable | description | default |
|--- | --- | --- |
| LOG_LEVEL | The possible values are: _**DEBUG**_, _**INFO**_, _**WARN**_, _**ERROR**_ | DEBUG |
| LOG_STYLE | Logging format: _**STANDARD**_, _**SIMPLE**_, _**JSON**_ | _**JSON**_ |
| JAVA_OPTS | The java options for the underlying jvm application | -Xms256M -Xmx1G -XX:+UseG1GC |
| COS_ADDRESS | container host | 0.0.0.0 |
| COS_PORT | container port | 9000 |
| COS_DEPLOYMENT_MODE | "docker" or "kubernetes" | "docker" |
| COS_DB_USER | journal, snapshot and read side offsets store username | postgres |
| COS_DB_PASSWORD | journal, snapshot and read side offsets store password | changeme |
| COS_DB_HOST | journal, snapshot and read side offsets store host | localhost |
| COS_DB_PORT | journal, snapshot and read side offsets store port | 5432 |
| COS_DB_NAME | journal, snapshot and read side offsets store db name | postgres |
| COS_DB_SCHEMA | journal, snapshot and read side offsets store db schema | public |
| COS_SNAPSHOT_FREQUENCY |Save snapshots automatically every Number of Events| 100 |
| COS_NUM_SNAPSHOTS_TO_RETAIN | Number of Aggregate Snapshot to persist to disk for swift recovery | 2 |
| COS_READ_SIDE_ENABLED | turn on readside or not | false |
| COS_READ_SIDE_OFFSET_DB_HOST | readside offset storage host | localhost |
| COS_READ_SIDE_OFFSET_DB_PORT | readside offset storage port | 5432 |
| COS_READ_SIDE_OFFSET_DB_USER | readside offset storage username | postgres |
| COS_READ_SIDE_OFFSET_DB_PASSWORD | readside offset storage password | changeme |
| COS_READ_SIDE_OFFSET_DB_SCHEMA | readside offset storage db scheme | postgres |
| COS_READ_SIDE_OFFSET_DB | readside offset storage db name | postgres |
| COS_READ_SIDE_OFFSET_STORE_TABLE | readside offset storage table name | read_side_offsets |
| COS_ENCRYPTION_CLASS | java class to use for encryption | <none> |
| COS_WRITE_SIDE_HOST | address of the gRPC writeSide handler service | <none> |
| COS_WRITE_SIDE_PORT | port for the gRPC writeSide handler service | <none> |
| COS_WRITE_SIDE_USE_TLS | use TLS for outbound gRPC calls to write side | false |
| COS_WRITE_SIDE_PROTO_VALIDATION | enable validation of the handler service states and events proto message FQN. If not set to `true` the validation will be skipped.  | false |
| COS_WRITE_SIDE_STATE_PROTOS | handler service states proto message FQN (fully qualified typeUrl). Format: `packagename.messagename`. This will be a comma separated list of values | <none> |
| COS_WRITE_SIDE_EVENT_PROTOS | handler service events proto message FQN (fully qualified typeUrl). Format: `packagename.messagename`. This will be a comma separated list of values | <none> |
| COS_SERVICE_NAME | service name | chiefofstate |
| COS_WRITE_SIDE_PROPAGATED_HEADERS | CSV of gRPC headers to propagate to write side handler | <none> |
| COS_WRITE_PERSISTED_HEADERS | CSV of gRPC headers to persist to journal (experimental) | <none> |
| COS_JOURNAL_LOGICAL_DELETION | Event deletion is triggered after saving a new snapshot. Old events would be deleted prior to old snapshots being deleted. | false |
| COS_COMMAND_HANDLER_TIMEOUT | Timeout required for the Aggregate to process command and reply. The value is in seconds. | 5 |

### Telemetry configuration

This library leverages the [io.opentelemetry](https://opentelemetry.io/docs/java/) library for both metrics and tracing
instrumentation. We only bundle in
the [OTLP](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/protocol/otlp.md) gRPC
exporter which should be used to push metrics and traces to
an [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/)
that should then propagate the same to desired monitoring services. Collection of telemetry data will be auto enabled
when a collector endpoint is configured.

The following options can be configured via environment variables.

Property | Required | Description
--- | --- | ---
COS_TELEMETRY_NAMESPACE | no | Namespace to be used to differentiate different chief of state deployments
COS_TELEMETRY_COLLECTOR_ENDPOINT | no | The grpc endpoint to be use to connect to an [opentelemetry collector](https://opentelemetry.io/docs/collector/) eg.`http://otlp.collector:4317`
COS_TRACE_PROPAGATORS | no | A comma separated list of [propagators](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/context/api-propagators.md#propagators-distribution) to enable. Defaults to `b3multi`. Valid values are **
b3**, **b3multi**, **tracecontext**, **baggage**, **jaeger** and **ottracer**

### Read side configurations

- SETTING_NAME - Supported setting names:
    - HOST - Read side host
    - PORT - Read side port
    - USE_TLS - Use TLS for read side calls
- READSIDE_ID - Unique id for the read side instance

| environment variable | description | default |
|--- | --- | --- |
| COS_READ_SIDE_CONFIG_<SETTING_NAME>_<READSIDE_ID> | readside configuration settings | <none> |
