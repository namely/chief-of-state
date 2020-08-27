FROM registry.namely.land/namely/sbt:1.3.6-2.13.1

all:
    # target running it all
    BUILD +test-all
    BUILD +docker-build

code:
    # copy relevant files in, save as a base image
    FROM registry.namely.land/namely/sbt:1.3.6-2.13.1
    COPY -dir project sbt-dist .scalafmt.conf build.sbt .
    COPY -dir api protos service .

    # clean and get dependencies
    RUN \
    --secret JFROG_USERNAME=+secrets/JFROG_USERNAME \
    --secret JFROG_PASSWORD=+secrets/JFROG_PASSWORD \
    sbt clean cleanFiles update

    # RUN sbt clean cleanFiles
    SAVE IMAGE

test-local:
    FROM +code
    ARG CODECOV_TOKEN=""
    ENV CODECOV_TOKEN=${CODECOV_TOKEN}
    RUN sbt coverage test coverageAggregate
    SAVE IMAGE

codecov:
    FROM +test-local
    ARG COMMIT_HASH=""
    ARG BRANCH_NAME=""
    ARG BUILD_NUMBER=""
    RUN curl -s https://codecov.io/bash | bash -s - -B "${BRANCH_NAME}" -C "${COMMIT_HASH}" -b "${BUILD_NUMBER}"

test-all:
    BUILD +test-local
    BUILD +codecov

docker-prep:
    # package the jars/executables
    FROM +code
    ARG VERSION=dev
    # TODO: use a simpler linux packager
    # https://www.scala-sbt.org/sbt-native-packager/formats/debian.html
    RUN sbt docker:stage
    RUN chmod -R u=rX,g=rX service/target/docker/stage
    SAVE ARTIFACT service/target/docker/stage

docker-build:
    # bundle into a slimmer, runnable container
    FROM openjdk:8-jre-alpine

    ARG VERSION=dev

    USER root

    RUN apk add --no-cache bash

    # create cos user for the service
    RUN adduser -S cos -s /bin/false

    # copy over files
    WORKDIR /opt/docker
    COPY --chown cos:root --dir +docker-prep/stage/opt +docker-prep/stage/1/opt +docker-prep/stage/2/opt /

    # set runtime user to cos
    USER cos

    ENTRYPOINT ["/opt/docker/bin/chiefofstate"]
    CMD []

    # build the image and push remotely (if all steps are successful)
    SAVE IMAGE --push registry.namely.land/namely/chief-of-state:${VERSION}

it-test:
    # use the image from +docker-build for IT test if desired
    FROM docker:19.03.7-dind
    # DOCKER LOAD +docker-build img
    # RUN --with-docker docker run img ...
