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

all:
    # target running it all
    BUILD +test
    BUILD +docker-build



# deb-package:
#     FROM +code

#     # TODO: install these beforehand
#     COPY -dir debian-setup.sh .
#     RUN bash ./debian-setup.sh

#     # build .deb file and save as artifact
#     RUN VERSION=1.0 sbt debian:packageBin
#     RUN mv ./service/target/chiefofstate*.deb ./service/target/cos.deb
#     # SAVE ARTIFACT service/target/cos.deb
#     SAVE ARTIFACT service/target

# docker-build:
#     FROM openjdk:8-jre-slim

#     USER root
#     COPY -dir +deb-package/cos.deb /opt
#     RUN apt-get install /opt/cos.deb
#     RUN rm /opt/cos.deb

#     USER chiefofstate
#     ENTRYPOINT chiefofstate
#     CMD []

    # # build the image and push remotely (if all steps are successful)
    # # https://docs.earthly.dev/earthfile#save-image
    # # SAVE IMAGE cos:latest --push registry.namely.land/namely/sample:<tag>
    # SAVE IMAGE cos:latest


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






# build the image and push remotely (if all steps are successful)
# https://docs.earthly.dev/earthfile#save-image
# SAVE IMAGE --push registry.namely.land/namely/sample:<tag>
