# Chief of State

[![Build Status](https://jenkins.namely.land/buildStatus/icon?job=chief-of-state/chief-of-state/master)](https://jenkins.namely.land/blue/organizations/jenkins/chief-of-state%2Fchief-of-state)
[![codecov](https://codecov.io/gh/namely/chief-of-state/branch/master/graph/badge.svg?token=82PZVNR2P1)](https://codecov.io/gh/namely/chief-of-state)

## Overview

![Architecture Diagram](img/architecture.png?raw=true "Title")

Chief-Of-State is a **_gRPC distributed event sourcing_** application that provides scalable, configurable, events and
state management strategies to relieve this responsibility from the developers.

Chief-Of-State is language agnostic, which means that services can be written in any language that supports gRPC.

Chief-Of-State can be bundled as a sidecar to the application it is providing events and state management or run it on
its own k8 pod.

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

### Documentation

The following docs are available:

- [Configuration options](./docs/configuration.md)
- [Docker Deployment](./docs/docker-deployment.md)
- [Kubernetes Deployment](./docs/kubernetes-deployment.md)

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

### Sample Projects

- [.NET Core](https://github.com/namely/cos-banking)
- [Golang](https://github.com/namely/cos-go-sample)
- [Python](https://github.com/namely/cos-python-sample)
