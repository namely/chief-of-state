# Deployment in Docker

The following section describes deployment in docker, including local development with docker-compose.

### Docker-specific Environment Variables

The following env vars can be set in addition to the [general configurations](./configuration.md).

| environment variable | description | default |
| --- | --- | --- |
| COS_DEPLOYMENT_MODE | "docker" | "docker" |
| COS_SERVICE_NAME | set this to the name of chief of state service in your docker compose | chiefofstate |
| COS_REPLICA_COUNT | wait for this many replicas before starting (not recommended to change) | 1 |
