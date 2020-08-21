#Earthfile

all:
    # target running it all
    BUILD +test
    BUILD +docker-build

code:
    # copy relevant files in, save as a base image
    FROM registry.namely.land/namely/sbt:1.3.6-2.13.1
    COPY -dir api db docker project protos sbt-dist service .
    COPY -dir .scalafmt.conf build.sbt .env .
    RUN sbt clean
    SAVE IMAGE

test:
    FROM +code
    RUN sbt clean coverage test coverageAggregate
    # RUN curl -s https://codecov.io/bash | bash || echo 'Codecov failed to upload'

docker-prep:
    # package the jars/executables
    FROM +code
    RUN sbt docker:stage
    RUN chmod -R u=rX,g=rX service/target/docker/stage
    RUN chmod a+r service/target/docker/stage
    SAVE ARTIFACT service/target/docker/stage

docker-build:
    # bundle into a slimmer, runnable container
    FROM openjdk:8-jre-slim

    USER root

    # create cos user for the service
    RUN groupadd -r cos && useradd --gid cos -r --shell /bin/false cos

    # copy over files
    WORKDIR /opt/docker
    COPY --dir +docker-prep/stage/opt +docker-prep/stage/1/opt +docker-prep/stage/2/opt /
    RUN chmod -R a+x /opt/docker/bin/chiefofstate && chown -R cos:root /opt/docker/bin/chiefofstate

    # set runtime user to cos
    USER cos

    ENTRYPOINT ["/opt/docker/bin/chiefofstate"]
    CMD []

    # build the image and push remotely (if all steps are successful)
    # https://docs.earthly.dev/earthfile#save-image
    # SAVE IMAGE cos:latest --push registry.namely.land/namely/sample:<tag>
    SAVE IMAGE cos:latest

it-test:
    # use the image from +docker-build for IT test if desired
    FROM docker:19.03.7-dind
    # DOCKER LOAD +docker-build img
    # RUN --with-docker docker run img ...

# docker-tags:
#     FROM registry.namely.land/namely/drone-docker-tag:latest
#     ARG EARTHLY_TARGET
#     ARG EARTHLY_GIT_ORIGIN_URL
#     ARG EARTHLY_GIT_HASH
#     ARG EARTHLY_TARGET_TAG_DOCKER
#     RUN printenv
