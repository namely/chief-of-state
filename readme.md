# Chief of State

[![Build Status](https://drone.namely.land/api/badges/namely/chief-of-state/status.svg)](https://drone.namely.land/namely/chief-of-state)

### notes

todo:
- remove "PLAY" from env var
- think about scaling out replicas in k8s (don't want to break the k8s replica)
- kafka configs

#### general variables
| key | descr | default |
--- | --- | ---
PLAY_HTTP_ADDRESS | container host | 0.0.0.0
PLAY_HTTP_PORT | container port | 9000
POSTGRES_USER | journal user | postgres
POSTGRES_PASSWORD | journal password | changeme
POSTGRES_HOST | journal host | localhost
POSTGRES_PORT | journal port | 5432
POSTGRES_DB | journal db name | postgres
POSTGRES_SCHEMA | journal db schema | public
KAFKA_BROKER | kafka broker | localhost:9092
HANDLER_SERVICE_HOST | address of the gRPC handler service | <none>
HANDLER_SERVICE_PORT | port for the gRPC handler service | <none>
SERVICE_NAME | chief of state name in tracing | chiefofstate
TEAM_NAME | |
TRACE_HOST | Jager collector/agent host | localhost
TRACE_PORT | Jaeger colletor/agent port | 14268

#### local dev options

| key | descr | default |
--- | --- | ---
DOCKER_SERVICE_NAME | name of chief of state in your docker compose | chiefofstate
DOCKER_REPLICA_COUNT | wait for this many replicas before starting (not recommended to change) | 1

#### production k8s options

| key | descr | default |
--- | --- | ---
POD_IP | IP of the pod running chief of state (see note below) | <none>
KUBERNETES_APP_LABEL | k8s metadata app label (must match exactly) that lagom uses when bootstrapping the cluster to discover its peers | <none>
KUBERNETES_REPLICA_COUNT | must match the replica count on your deployment | 1

**Kubernetes deployment**

Pod IP should be dynamic with a k8s enviornment setting, such as:
```yaml
env:
  - name: POD_IP
    valueFrom:
      fieldRef:
        apiVersion: v1
        fieldPath: status.podIP
```

K8s configuration for tracing agent at Namely
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
