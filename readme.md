## Chief of State

[![Build Status](https://drone.namely.land/api/badges/namely/chief-of-state/status.svg)](https://drone.namely.land/namely/chief-of-state)

gRPC distributed event sourcing

### Notes

todo:

- think about scaling out replicas in k8s (don't want to break the k8s replica)
- kafka configs

### Global environment variables

| key | description | default |
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
| HANDLER_SERVICE_HOST | address of the gRPC handler service | <none> |
| HANDLER_SERVICE_PORT | port for the gRPC handler service | <none> |
| HANDLER_SERVICE_STATE_PROTO | handler service state proto message FQN (fully qualified typeUrl). Format: `packagename.messagename` | <none> |
| HANDLER_SERVICE_EVENTS_PROTOS | handler service events proto message FQN (fully qualified typeUrl). Format: `packagename.messagename`. This will be a comma separated list of values | <none> |
| COS_SERVICE_NAME | chief of state name in tracing | chiefofstate |
| TEAM_NAME | |
| TRACE_HOST | Jaeger collector/agent host | localhost |
| TRACE_PORT | Jaeger colletor/agent port | 14268 |

### Local dev options

| key | description | default |
| --- | --- | --- |
| COS_DOCKER_SERVICE_NAME | name of chief of state in your docker compose | chiefofstate |
| COS_DOCKER_REPLICA_COUNT | wait for this many replicas before starting (not recommended to change) | 1 |

### Production k8s options

| key | description | default |
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
