# Chief of State

[![Build Status](https://drone.namely.land/api/badges/namely/chief-of-state/status.svg)](https://drone.namely.land/namely/chief-of-state)
[![codecov](https://codecov.io/gh/namely/chief-of-state/branch/master/graph/badge.svg?token=82PZVNR2P1)](https://codecov.io/gh/namely/chief-of-state)

## Overview

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

### Local dev

- For local development, the following pre-requisites are necessary: 
    - Install at least Java 8 [Java download](https://www.oracle.com/java/technologies/javase-downloads.html) on your local dev machine.
    
    -  [Sbt](https://www.scala-sbt.org/download.html) must be installed on the development machine.
    
    - [Docker](https://www.docker.com/get-started)  must be installed on the development machine.
    
    - Set the [global](#global-environment-variables) in addition with the [local](#local-dev-options) ones. Check a sample docker-compose file inside the docker folder.
    
    - Run `sbt dockerComposeUp` to start the application
    
    - Run `sbt dockerComposeStop` to gracefully stop the application

### Inside a _docker-compose_ file

- Pull the docker image from `registry.namely.land/namely/chief-of-state:<tag>` where `tag` is the latest release tag.
  
- Set the environment variable listed [here](#global-environment-variables) in addition with the [local](#local-dev-options) ones.

- Set the following environment variable `JAVA_OPTS: "-Dconfig.resource=docker.conf"`

- Happy hacking :)

### Global environment variables

| environment variable | description | default |
|--- | --- | --- |
| LOG_LEVEL | The possible values are: _**DEBUG**_, _**INFO**_, _**WARN**_, _**ERROR**_ | DEBUG |
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
| COS_NUM_SNAPSHOTS_TO_RETAIN | Number of Aggregate Snapshot to persist to disk for swift recovery | 2 |
| COS_READ_SIDE_ENABLED | turn on readside or not | false |
| COS_READ_SIDE_OFFSET_DB_HOST | readside offset storage host | localhost |
| COS_READ_SIDE_OFFSET_DB_PORT | readside offset storage port | 5432 |
| COS_READ_SIDE_OFFSET_DB_USER | readside offset storage username | postgres |
| COS_READ_SIDE_OFFSET_DB_PASSWORD | readside offset storage password | changeme |
| COS_READ_SIDE_OFFSET_DB_SCHEMA | readside offset storage db scheme | postgres |
| COS_READ_SIDE_OFFSET_DB | readside offset storage db name | postgres |
| COS_ENCRYPTION_CLASS | java class to use for encryption | io.superflat.lagompb.encryption.NoEncryption |
| WRITE_SIDE_HANDLER_SERVICE_HOST | address of the gRPC writeSide handler service | <none> |
| WRITE_SIDE_HANDLER_SERVICE_PORT | port for the gRPC writeSide handler service | <none> |
| READ_SIDE_HANDLER_SERVICE_HOST | address of the gRPC readSide handler service. This must be set when readSide is turned on | <none> |
| READ_SIDE_HANDLER_SERVICE_PORT | port for the gRPC readSide handler service. This must be set when readSide is turned on | <none> |
| HANDLER_SERVICE_STATES_PROTO | handler service states proto message FQN (fully qualified typeUrl). Format: `packagename.messagename`.  This will be a comma separated list of values | <none> |
| HANDLER_SERVICE_EVENTS_PROTOS | handler service events proto message FQN (fully qualified typeUrl). Format: `packagename.messagename`. This will be a comma separated list of values | <none> |
| COS_SERVICE_NAME | service name | chiefofstate |
| TEAM_NAME | |
| TRACE_HOST | Jaeger collector/agent host | localhost |
| TRACE_PORT | Jaeger collector/agent port | 14268 |

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
- [Python](https://github.com/namely/cos-python-sample)

### Notes

todo:

- think about scaling out replicas in k8s (don't want to break the k8s replica)
