# Chief of State

[![Build Status](https://drone.namely.land/api/badges/namely/chief-of-state/status.svg)](https://drone.namely.land/namely/chief-of-state)
[![codecov](https://codecov.io/gh/namely/chief-of-state/branch/master/graph/badge.svg?token=82PZVNR2P1)](https://codecov.io/gh/namely/chief-of-state)

## Overview

Chief-Of-State is a **_gRPC distributed event sourcing_** application that provides scalable, configurable, events and state management strategies to relieve this responsibility from the developers. 

Chief-Of-State is language agnostic, which means that services can be written in any language that supports gRPC. 

Chief-Of-State can be bundled as a sidecar to the application it is providing events and state management or run it on its own k8 pod. 

Chief-Of-State heavily relies on the robustness of [lagom-pb](https://github.com/super-flat/lagom-pb). 

## Features

- Journal, Snapshot are serialized using google protocol buffer message format.

- Out of the box clustering and powerful events and domain entities sharding with split-brain-resolver algorithm.

- Out of the box entities passivation mechanism to free resources whenever necessary.

- All events, state are defined using google protocol buffer message format and persisted to postgres.

- Additional meta data are provided to your events via the `MetaData`.

- Commands and Events handlers via gRPC.

- Read Side processor via gRPC (every persisted event is made available when the read side is turn on). 

- Out of the box Read Side offset management residing in the Chief-Of-State readSide store (postgresql).

- Out of the box observability.

- Out of the box configurable k8 deployment.

### Global environment variables

| environment variable | description | default |
|--- | --- | --- |
| COS_ADDRESS | container host | 0.0.0.0 |
| COS_PORT | container port | 9000 |
| COS_POSTGRES_USER | journal, snapshot and read side offsets store username | postgres |
| COS_POSTGRES_PASSWORD | journal, snapshot and read side offsets store password | changeme |
| COS_POSTGRES_HOST | journal, snapshot and read side offsets store host | localhost |
| COS_POSTGRES_PORT | journal, snapshot and read side offsets store port | 5432 |
| COS_POSTGRES_DB | journal, snapshot and read side offsets store db name | postgres |
| COS_POSTGRES_SCHEMA | journal, snapshot and read side offsets store db schema | public |
| COS_KAFKA_BROKER | kafka broker | localhost:9092 |
| COS_EVENTS_BATCH_THRESHOLD | Number of Events to batch persist | 100 |
| COS_NUM_SNAPSHOTS_TO_RETAIN | Number of Aggregate Snaphsot to persist to disk for swift recovery | 2 |
| COS_READ_SIDE_ENABLED | turn on readside or not | false |
| COS_READ_SIDE_OFFSET_DB_HOST | readside offset storage host | localhost |
| COS_READ_SIDE_OFFSET_DB_PORT | readside offset storage port | 5432 |
| COS_READ_SIDE_OFFSET_DB_USER | readside offset storage username | postgres |
| COS_READ_SIDE_OFFSET_DB_PASSWORD | readside offset storage password | changeme |
| COS_READ_SIDE_OFFSET_DB_SCHEMA | readside offset storage db scheme | postgres |
| COS_READ_SIDE_OFFSET_DB | readside offset storage db name | postgres |
| WRITE_SIDE_HANDLER_SERVICE_HOST | address of the gRPC writeSide handler service | <none> |
| WRITE_SIDE_HANDLER_SERVICE_PORT | port for the gRPC writeSide handler service | <none> |
| READ_SIDE_HANDLER_SERVICE_HOST | address of the gRPC readSide handler service. This must be set when readSide is turned on | <none> |
| READ_SIDE_HANDLER_SERVICE_PORT | port for the gRPC readSide handler service. This must be set when readSide is turned on | <none> |
| HANDLER_SERVICE_STATE_PROTO | handler service state proto message FQN (fully qualified typeUrl). Format: `packagename.messagename` | <none> |
| HANDLER_SERVICE_EVENTS_PROTOS | handler service events proto message FQN (fully qualified typeUrl). Format: `packagename.messagename`. This will be a comma separated list of values | <none> |
| COS_SERVICE_NAME | chief of state name in tracing | chiefofstate |
| TEAM_NAME | |
| TRACE_HOST | Jaeger collector/agent host | localhost |
| TRACE_PORT | Jaeger colletor/agent port | 14268 |

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

- K8s configuration for tracing agent at Namely

```yaml
- name: NODE_NAME
  valueFrom:
    fieldRef:
      fieldPath: spec.nodeName
- name: TRACE_HOST
  value: $(NODE_NAME)
- name: TRACE_PORT
  value: "9080"
```

### Sample Projects

- [.NET Core](https://github.com/namely/cos-banking)
- [Golang](https://github.com/namely/cos-go-sample)

### Notes

todo:

- think about scaling out replicas in k8s (don't want to break the k8s replica)
