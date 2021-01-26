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
| COS_DB_CREATE_TABLES | when enabled create both writeside journal/snapshot store tables and readside offset store if readside settings enabled. | false |
| COS_DB_USER | journal, snapshot and read side offsets store username | postgres |
| COS_DB_PASSWORD | journal, snapshot and read side offsets store password | changeme |
| COS_DB_HOST | journal, snapshot and read side offsets store host | localhost |
| COS_DB_PORT | journal, snapshot and read side offsets store port | 5432 |
| COS_DB_NAME | journal, snapshot and read side offsets store db name | postgres |
| COS_DB_SCHEMA | journal, snapshot and read side offsets store db schema | public |
| COS_EVENTS_BATCH_THRESHOLD | Number of Events to batch persist | 100 |
| COS_NUM_SNAPSHOTS_TO_RETAIN | Number of Aggregate Snapshot to persist to disk for swift recovery | 2 |
| COS_READ_SIDE_ENABLED | turn on readside or not | false |
| COS_READ_SIDE_OFFSET_DB_HOST | readside offset storage host | localhost |
| COS_READ_SIDE_OFFSET_DB_PORT | readside offset storage port | 5432 |
| COS_READ_SIDE_OFFSET_DB_USER | readside offset storage username | postgres |
| COS_READ_SIDE_OFFSET_DB_PASSWORD | readside offset storage password | changeme |
| COS_READ_SIDE_OFFSET_DB_SCHEMA | readside offset storage db scheme | postgres |
| COS_READ_SIDE_OFFSET_DB | readside offset storage db name | postgres |
| COS_READ_SIDE_OFFSET_STORE_TABLE | readside offset storage table name | read_side_offsets |
| COS_READ_SIDE_OFFSET_USE_LOWERCASE_SCHEMA | It recommended that old applications set it to **_false_**.  | true |
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
| COS_JAEGER_ENABLED | Enable tracing (see below for more options) | false |
| COS_PROMETHEUS_ROUTE | route for prometheus to scrap metrics | "metrics" |
| COS_PROMETHEUS_PORT | port for prometheus to scrap metrics | 9102 |

### Tracing configuration

This library leverages the [io.opentracing](https://opentracing.io/guides/java/) library
and [Jaeger tracing instrumentation library](https://github.com/jaegertracing/jaeger-client-java).

To enable tracing, set the env var `COS_JAEGER_ENABLED = true`.

The following options can be configured via environment
variables ([click here for more settings](https://github.com/jaegertracing/jaeger-client-java/blob/master/jaeger-core/README.md))
.

Property | Required | Description
--- | --- | ---
JAEGER_SERVICE_NAME | yes | The service name
JAEGER_AGENT_HOST | no | The hostname for communicating with agent via UDP
JAEGER_AGENT_PORT | no | The port for communicating with agent via UDP
JAEGER_ENDPOINT | no | The traces endpoint, in case the client should connect directly to the Collector, like http://jaeger-collector:14268/api/traces
JAEGER_PROPAGATION | no | Comma separated list of formats to use for propagating the trace context. Defaults to the standard Jaeger format. Valid values are **

jaeger**, **b3**, and **w3c**
JAEGER_SAMPLER_TYPE | no |
The [sampler type](https://www.jaegertracing.io/docs/latest/sampling/#client-sampling-configuration)
JAEGER_TAGS | no | A comma separated list of `name = value` tracer level tags, which get added to all reported spans.
The value can also refer to an environment variable using the format `${envVarName:default}`, where the `:default` is
optional, and identifies a value to be used if the environment variable cannot be found

### Read side configurations

- SETTING_NAME - Supported setting names:
    - HOST - Read side host
    - PORT - Read side port
    - USE_TLS - Use TLS for read side calls
- READSIDE_ID - Unique id for the read side instance

| environment variable | description | default |
|--- | --- | --- |
| COS_READ_SIDE_CONFIG_<SETTING_NAME>_<READSIDE_ID> | readside configuration settings | <none> |
