# Chief of State

[![Build Status](https://jenkins.namely.land/buildStatus/icon?job=chief-of-state/chief-of-state/master)](https://jenkins.namely.land/blue/organizations/jenkins/chief-of-state%2Fchief-of-state)
[![codecov](https://codecov.io/gh/namely/chief-of-state/branch/master/graph/badge.svg?token=82PZVNR2P1)](https://codecov.io/gh/namely/chief-of-state)

## Overview

![Architecture Diagram](img/architecture.png?raw=true "Title")

Chief-Of-State is a **_gRPC distributed event sourcing_** application that provides scalable, configurable, events and state management strategies to relieve this responsibility from the developers.

Chief-Of-State is language agnostic, which means that services can be written in any language that supports gRPC.

Chief-Of-State can be bundled as a sidecar to the application it is providing events and state management or run it on its own k8 pod.

Chief-Of-State heavily relies on the robustness of [lagom-pb](https://github.com/super-flat/lagom-pb).

## Features

- Journal and Snapshot serialization using google protocol buffer message format.

- Out of the box clustering and powerful events and domain entities sharding with split-brain-resolver algorithm.

- Out of the box entities passivation mechanism to free resources whenever necessary.

- All events, state serialization using google protocol buffer message format and persisted to postgres.

- Additional meta data are provided to your events via the `MetaData`.

- Commands and Events handlers via gRPC.

- Read Side processor via gRPC (every persisted event is available when the read side is turn on).

- Out of the box Read Side offset management residing in the Chief-Of-State readSide store (postgresql).

- Out of the box observability.

- Out of the box configurable k8 deployment.

### Locally build / test

```bash
# install earth cli
brew install earthly

# locally build the image
earth +docker-build

# run local cluster with docker/docker-compose.yml
docker-compose -f ./docker/docker-compose.yml --project-directory . up -d

# observe containers
docker-compose -f ./docker/docker-compose.yml --project-directory . ps

# shut it down
docker-compose -f ./docker/docker-compose.yml down -t 0 --remove-orphans
```

### Inside a _docker-compose_ file

- Pull the docker image from `chief-of-state:<tag>` where `tag` is the latest release tag.

- Set the environment variable listed [here](#global-environment-variables) in addition with the [local](#local-dev-options) ones.

- Set the following environment variable `JAVA_OPTS: "-Dconfig.resource=docker.conf"`

- Happy hacking :)

### Global environment variables

| environment variable | description | default |
|--- | --- | --- |
| LOG_LEVEL | The possible values are: _**DEBUG**_, _**INFO**_, _**WARN**_, _**ERROR**_ | DEBUG |
| COS_ADDRESS | container host | 0.0.0.0 |
| COS_PORT | container port | 9000 |
| COS_STORES_AUTO_CREATE | when enabled create both writeside journal/snapshot store tables and readside offset store if readside settings enabled. | false |
| COS_POSTGRES_USER | journal, snapshot and read side offsets store username | postgres |
| COS_POSTGRES_PASSWORD | journal, snapshot and read side offsets store password | changeme |
| COS_POSTGRES_HOST | journal, snapshot and read side offsets store host | localhost |
| COS_POSTGRES_PORT | journal, snapshot and read side offsets store port | 5432 |
| COS_POSTGRES_DB | journal, snapshot and read side offsets store db name | postgres |
| COS_POSTGRES_SCHEMA | journal, snapshot and read side offsets store db schema | public |
| COS_EVENTS_BATCH_THRESHOLD | Number of Events to batch persist | 100 |
| COS_NUM_SNAPSHOTS_TO_RETAIN | Number of Aggregate Snapshot to persist to disk for swift recovery | 2 |
| COS_READ_SIDE_ENABLED | turn on readside or not | false |
| COS_READ_SIDE_OFFSET_DB_HOST | readside offset storage host | localhost |
| COS_READ_SIDE_OFFSET_DB_PORT | readside offset storage port | 5432 |
| COS_READ_SIDE_OFFSET_DB_USER | readside offset storage username | postgres |
| COS_READ_SIDE_OFFSET_DB_PASSWORD | readside offset storage password | changeme |
| COS_READ_SIDE_OFFSET_DB_SCHEMA | readside offset storage db scheme | postgres |
| COS_READ_SIDE_OFFSET_DB | readside offset storage db name | postgres |
| COS_READ_SIDE_OFFSET_STORE_TABLE | readside offset storage table name | AKKA_PROJECTION_OFFSET_STORE |
| COS_ENCRYPTION_CLASS | java class to use for encryption | <none> |
| WRITE_SIDE_HANDLER_SERVICE_HOST | address of the gRPC writeSide handler service | <none> |
| WRITE_SIDE_HANDLER_SERVICE_PORT | port for the gRPC writeSide handler service | <none> |
| HANDLER_SERVICE_ENABLE_PROTO_VALIDATION | enable validation of the handler service states and events proto message FQN. If not set to `true` the validation will be skipped.  | false |
| HANDLER_SERVICE_STATES_PROTOS | handler service states proto message FQN (fully qualified typeUrl). Format: `packagename.messagename`.  This will be a comma separated list of values | <none> |
| HANDLER_SERVICE_EVENTS_PROTOS | handler service events proto message FQN (fully qualified typeUrl). Format: `packagename.messagename`. This will be a comma separated list of values | <none> |
| COS_SERVICE_NAME | service name | chiefofstate |
| TRACE_HOST | Jaeger collector/agent host | localhost |
| TRACE_PORT | Jaeger collector/agent port | 14268 |
| COS_WRITE_PROPAGATED_HEADERS | CSV of gRPC headers to propagate to write side handler | <none> |
| COS_WRITE_PERSISTED_HEADERS | CSV of gRPC headers to persist to journal (experimental) | <none> |
| COS_DISABLE_SNAPSHOTS | Disable aggregate state snapshots to be persisted | false |
| COS_JOURNAL_LOGICAL_DELETION | Event deletion is triggered after saving a new snapshot. Old events would be deleted prior to old snapshots being deleted. | false |

### Read side configurations

- SETTING_NAME - Supported setting names:
  - HOST - Read side host
  - PORT - Read side port
- READSIDE_ID - Unique id for the read side instance

| environment variable | description | default |
|--- | --- | --- |
| COS_READ_SIDE_CONFIG_<SETTING_NAME>_<READSIDE_ID> | readside configuration settings | <none> |

### Local dev options

| environment variable | description | default |
| --- | --- | --- |
| COS_DOCKER_SERVICE_NAME | name of chief of state in your docker compose | chiefofstate |
| COS_DOCKER_REPLICA_COUNT | wait for this many replicas before starting (not recommended to change) | 1 |

### Production k8s options

| environment variable | description | default |
| --- | --- | --- |
| POD_IP | IP of the pod running chief of state (see note below) | <none> |
| COS_KUBERNETES_APP_LABEL | k8s metadata app label (must match exactly) that lagom uses when bootstrapping the cluster to discover its peers | <none> |
| COS_KUBERNETES_REPLICA_COUNT | must match the replica count on your deployment | 1 |

#### Kubernetes deployment

- Pod IP should be dynamic with a k8s enviornment setting, such as:

```yaml
env:
  - name: POD_IP
    valueFrom:
      fieldRef:
        apiVersion: v1
        fieldPath: status.podIP
```

### Sample Projects

- [.NET Core](https://github.com/namely/cos-banking)
- [Golang](https://github.com/namely/cos-go-sample)
- [Python](https://github.com/namely/cos-python-sample)
