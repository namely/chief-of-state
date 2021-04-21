# Getting Started

This document describes the anatomy of a common COS application and the language-agnostic steps
to get started. See the sample applications for language-specific examples.

## Generating the protos

chief-of-state defines its public interfaces as [gRPC](https://grpc.io/) services
and objects as [protobufs](https://developers.google.com/protocol-buffers) in
`.proto` files. You will need to generate these for your application language
to interact with chief-of-state and also to implement the requried write-handler
and read-handler methods (more on those later).

The protos are located in a github repo: https://github.com/namely/chief-of-state-protos

A popular way of incorporating them into your project is via git submodules,
which you can add like so:
```sh
# adds COS protos to proto/cos
git submodule add git@github.com:namely/chief-of-state-protos ./protos/cos
```

From there, you can generate the COS interfaces for your language (see the
official quick start guide for your language https://grpc.io/docs/languages/).

As an example, one would use [protoc](https://grpc.io/docs/protoc-installation/)
to generate java source code like so:
```sh
export SRC_DIR=protos/cos
export DST_DIR=build/gen
# generate everything in protos/cos/chief_of_state/v1/common.proto
protoc -I=$SRC_DIR --java_out=$DST_DIR $SRC_DIR/foo.proto
# generate ALL .proto files in protos/cos
find $SRC_DIR -name "*.proto" | xargs -I{} protoc -I=$SRC_DIR --java_out=$DST_DIR {}
```

**Pro tip:** many languages have wrappers around protoc to make this easier,
like [scalap-pb](https://scalapb.github.io/) for scala (which chief-of-state
uses internally). Namely also offers an open-source docker-based solution
[namely/docker-protoc](https://github.com/namely/docker-protoc) with support
for many languages.

## Implement a "Write Side"

Drawing from CQRS terminology, COS defines a [writeside interface](https://github.com/namely/chief-of-state-protos/blob/master/chief_of_state/v1/writeside.proto) that you must implement to define how state
is created and updated.

### Methods
`HandleCommand` accepts a command (think "request") and a prior state and returns an event.
For example, given a command to `UpdateUserEmail` and a User, this RPC might return
an event called `UserEmailUpdated`. This method is encouraged to throw exceptions
(as gRPC error statuses) **if** the inbound command (request) is invalid.

`HandleEvent` accepts an event and the prior state of an entity and returns a new state.
For example, given a UserEmailUpdated event and a User, this RPC would return a
new User instance with the email updated. It's advisable to do all your
validations in `HandleCommand` such that this method is unlikely to encounter
invalid data. If `HandleCommand` fails, `HandleEvent` is not called for that
command.

### Registration
Register your write handler service as COS environment variables like so:

```yaml
# set the host and port for your write handler gRPC server
COS_WRITE_SIDE_HOST: localhost
COS_WRITE_SIDE_PORT: 50051
```

(If you are new to setting up COS, see the full [configuration](docs/configuration.md) guide.)

### Sending requests to COS
Once you have implemneted a write handler and registered it with COS, you are
able to start sending requests to chief-of-state with the chief-of-state
[gRPC client methods](https://github.com/namely/chief-of-state-protos/blob/master/chief_of_state/v1/service.proto).

`ProcessCommand` accepts a command (request) and an entity ID, and processes
that command by forwarding it to your write handler methods. Commands for a given
entity are guaranteed to be processed serially, but commands for unrelated entities
can be processed in parallel.

`GetState` accepts an entity ID and returns the latest state of that entity. Use
this like a key/value lookup.

## Implement a "Read Side"

COS defines a [readside interface](https://github.com/namely/chief-of-state-protos/blob/master/chief_of_state/v1/readside.proto)
and allows you to implement many "readsides" (from CQRS "read side") to
stream your events and state to external destinations. For example, you might
implement a readside that writes to your applications private elasticsearch
cluster for serving fancy search queries, and you could implement a second
readside that publishes your event to kafka for consumption by other services.

The COS readside processor guarantees that events are served in the order they were
persisted to the journal per entity. Offsets are tracked for per readside, so
adding a readside later will start from the beginning of your event journal (or
the oldest available event if you have limited retention).

A read handler implements a single method, `HandleReadSide` that accepts
your event, the "resulting state" following that event, and some COS metadata
about the event. In response, your read handler can either acknowledge the message
and advance the offset, or fail (in which case COS will send the message again).

Once you have your read handler server ready, register it with the following
configurations:
```yaml
# this enables the read side processor for COS
COS_READ_SIDE_ENABLED: true
# this defines a read side called "elastic" on localhost @ port 50052
COS_READ_SIDE_CONFIG__HOST__ELASTIC: localhost
COS_READ_SIDE_CONFIG__PORT__ELASTIC: 50052
# this defines a read side called "kafka" on localhost @ port 50053
COS_READ_SIDE_CONFIG__HOST__KAFKA: localhost
COS_READ_SIDE_CONFIG__PORT__KAFKA: 50053
```

See the readside configuration settings in our [configuration docs](./docs/configuration.md) for more details.
