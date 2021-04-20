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

## Implement your "Write Handler"
