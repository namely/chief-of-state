#Earthfile

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

deb-package:
    FROM +code

    # TODO: install these beforehand
    COPY -dir debian-setup.sh .
    RUN bash ./debian-setup.sh

    # build .deb file and save as artifact
    RUN VERSION=1.0 sbt debian:packageBin
    RUN mv ./service/target/chiefofstate*.deb ./service/target/cos.deb
    SAVE ARTIFACT service/target/cos.deb

docker-build:
    FROM openjdk:8-jre-slim

    USER root
    WORKDIR /opt/docker
    COPY -dir +deb-package/cos.deb /opt
    RUN apt-get install /opt/cos.deb

    USER chiefofstate
    ENTRYPOINT chiefofstate
    CMD []

    # build the image and push remotely (if all steps are successful)
    # https://docs.earthly.dev/earthfile#save-image
    # SAVE IMAGE cos:latest --push registry.namely.land/namely/sample:<tag>
    SAVE IMAGE cos:latest

all:
    # target running it all
    BUILD +test
    BUILD +docker-build

## save the staged files to host
# stage-local:
#     FROM +compile
#     RUN sbt docker:stage
#     SAVE ARTIFACT service/target/docker/stage AS LOCAL .target

## use local files from host to build docker image
# build-local:
#     FROM DOCKERFILE .target/
#     SAVE IMAGE

# build-dind:
#     FROM docker:19.03.7-dind
#     COPY --dir +staged/staged /
#     RUN ls -la /staged
#     RUN --with-docker docker build /staged/

# docker-tags:
#     FROM registry.namely.land/namely/drone-docker-tag:latest
#     ARG EARTHLY_TARGET
#     ARG EARTHLY_GIT_ORIGIN_URL
#     ARG EARTHLY_GIT_HASH
#     ARG EARTHLY_TARGET_TAG_DOCKER
#     RUN printenv



# write the
# stage-docker:
#     FROM +compile
#     RUN sbt docker:stage
#     SAVE ARTIFACT service/target/docker/stage /staged
#     SAVE IMAGE

# # temporary target that imitates the generated dockerfile
# docker-stage:
#     FROM openjdk:8
#     USER root
#     RUN id -u demiourgos728 1>/dev/null 2>&1 || (( getent group 0 1>/dev/null 2>&1 || ( type groupadd 1>/dev/null 2>&1 && groupadd -g 0 root || addgroup -g 0 -S root )) && ( type useradd 1>/dev/null 2>&1 && useradd --system --create-home --uid 1001 --gid 0 demiourgos728 || adduser -S -u 1001 -G root demiourgos728 ))
#     WORKDIR /opt/docker
#     COPY +stage-docker/staged/opt /opt
#     COPY +stage-docker/staged/1/opt /opt
#     COPY +stage-docker/staged/2/opt /opt
#     RUN ["chmod", "-R", "u=rX,g=rX", "/opt/docker"]
#     RUN chown -R demiourgos728:root /opt/docker
#     USER 1001:0
#     ENTRYPOINT ["/opt/docker/bin/chiefofstate"]
#     CMD []
#     SAVE IMAGE



# build the image and push remotely (if all steps are successful)
# https://docs.earthly.dev/earthfile#save-image
# SAVE IMAGE --push registry.namely.land/namely/sample:<tag>
