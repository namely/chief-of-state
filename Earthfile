FROM busybox:1.32

all:
    # target running it all
    BUILD +test-all
    BUILD +docker-build

code:
    # copy relevant files in, save as a base image
    FROM +sbt
    # copy configurations
    COPY -dir project .scalafmt.conf build.sbt .
    # copy proto definitions
    COPY -dir proto .
    # copy code
    COPY code/service/src ./code/service/src
    # clean and get dependencies
    RUN sbt clean cleanFiles update protocGenerate
    # save base image for use downstream
    SAVE IMAGE

docker-prep:
    # package the jars/executables
    FROM +code
    ARG VERSION=dev
    # TODO: use a simpler linux packager
    # https://www.scala-sbt.org/sbt-native-packager/formats/debian.html
    RUN sbt docker:stage
    RUN chmod -R u=rX,g=rX code/service/target/docker/stage
    SAVE ARTIFACT code/service/target/docker/stage

docker-build:
    # bundle into a slimmer, runnable container
    FROM openjdk:8-jre-slim

    ARG VERSION=dev

    USER root

    # create cos user for the service
    RUN groupadd -r cos && useradd --gid cos -r --shell /bin/false cos

    # copy over files
    WORKDIR /opt/docker
    COPY --chown cos:root --dir +docker-prep/stage/opt +docker-prep/stage/1/opt +docker-prep/stage/2/opt /

    # set runtime user to cos
    USER cos

    ENTRYPOINT ["/opt/docker/bin/chiefofstate"]
    CMD []

    # build the image and push remotely (if all steps are successful)
    SAVE IMAGE --push registry.namely.land/namely/chief-of-state:${VERSION}


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

it-test:
    # use the image from +docker-build for IT test if desired
    FROM docker:19.03.7-dind
    # DOCKER LOAD +docker-build img
    # RUN --with-docker docker run img ...

sbt:
    # TODO: move this to a central image
    FROM openjdk:8u232-jdk-stretch

    # Install sbt
    ARG SBT_VERSION=1.3.6
    RUN \
        curl -L -o sbt.deb https://dl.bintray.com/sbt/debian/sbt-${SBT_VERSION}.deb && \
        dpkg -i sbt.deb && \
        rm sbt.deb && \
        apt-get update && \
        apt-get install sbt && \
        sbt sbtVersion

    SAVE IMAGE
