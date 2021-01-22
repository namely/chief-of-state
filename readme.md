# Chief of State

![Build](https://github.com/namely/chief-of-state/workflows/Build/badge.svg?branch=master)
[![codecov](https://codecov.io/gh/namely/chief-of-state/branch/master/graph/badge.svg?token=82PZVNR2P1)](https://codecov.io/gh/namely/chief-of-state)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Gitter](https://badges.gitter.im/namely/chief-of-state.svg)](https://gitter.im/namely/chief-of-state?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
[![Docker Hub](https://img.shields.io/badge/docker%20hub-namely-blue)](https://hub.docker.com/repository/docker/namely/chief-of-state)
![Docker Pulls](https://img.shields.io/docker/pulls/namely/chief-of-state)

## Overview

Chief-of-state (COS) is a clustered persistence framework for building event sourced applications. COS supports CQRS and
event-sourcing through simple, language-agnostic interfaces via gRPC, and it allows developers to describe their schema
with Protobuf. Under the hood, COS leverages [Akka](https://akka.io/)
to scale out and guarantee performant, reliable persistence.

Chief-of-state was built by Namely with the following principles:

* Wire format should be the same as persistence
* Scaling should not require re-architecture
* Developers shouldn't face race conditions or database locks
* Rules should be enforced with interfaces
* Event sourcing is valuable, but challenging to implement
* An ideal event-sourcing datastore would offer random access by key, streaming, and atomic writes

## Anatomy of a chief-of-state app

Developers implement two gRPC interfaces: a write handler for building state and, optionally, many read handlers for
reacting to state changes.

![Architecture Diagram](img/architecture.png?raw=true "Title")

### Write Handler

Developers describe state mutations by implementing two RPC’s in
the [WriteSideHandlerService](https://github.com/namely/chief-of-state-protos/blob/master/chief_of_state/v1/writeside.proto):

- `HandleCommand` accepts a command and the prior state of an entity and returns an Event. For example, given a command
  to UpdateUserEmail and a User, this RPC might return UserEmailUpdated.
- `HandleEvent` accepts an event and the prior state of an entity and returns a new state. For example, given a
  UserEmailUpdated event and a User, this RPC would return a new User instance with the email updated.

### Read Handler

In response to state mutations, COS is able to send changes to
many [ReadSideHandlerService](https://github.com/namely/chief-of-state-protos/blob/master/chief_of_state/v1/readside.proto)
implementations, which may take any action. COS guarantees at-least-once delivery of events and resulting state to each
read side in the order they were persisted.

Some potential read side handlers might:

- Write state changes to a special data store like elastic
- Publish changes to kafka topics
- Send notifications to users in response to specific events

## Features

- Journal and Snapshot serialization using google protocol buffer message format
- Preconfigured Akka clustering and and domain entity sharding with the split-brain-resolver algorithm
- Automatic caching and entity passivation
- Automatic configuration of postgres storage on boot
- Opentelemetry integration for tracing and prometheus metrics
- Direct integration to Kubernetes to form a cluster

### Documentation

The following docs are available:

- [Configuration options](./docs/configuration.md)
- [Docker Deployment](./docs/docker-deployment.md)
- [Kubernetes Deployment](./docs/kubernetes-deployment.md)

### Locally build / test

```bash
# install earthly cli
brew install earthly

# locally build the image
earthly +docker-build

# run local cluster with docker/docker-compose.yml
docker-compose -f ./docker/docker-compose.yml --project-directory . up -d

# observe containers
docker-compose -f ./docker/docker-compose.yml --project-directory . ps

# shut it down
docker-compose -f ./docker/docker-compose.yml down -t 0 --remove-orphans
```

### Sample Projects

[Python](https://github.com/namely/cos-python-sample)

