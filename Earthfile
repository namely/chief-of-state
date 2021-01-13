FROM busybox:1.32

all:
    # target running it all
    BUILD +test-all
    BUILD +docker-build

code:
    # copy relevant files in, save as a base image
    FROM +sbt

    # create user & working dir for sbt
    ARG BUILD_DIR="/build"
    ARG BUILD_USR="builder"

    USER root

    RUN mkdir $BUILD_DIR && \
        chmod 777 /$BUILD_DIR && \
        useradd -s /bin/bash -d $BUILD_DIR $BUILD_USR && \
        chown -R $BUILD_USR:root $BUILD_DIR

    WORKDIR $BUILD_DIR
    USER $BUILD_USR

    # copy configurations
    COPY --chown $BUILD_USR:root .scalafmt.conf build.sbt .
    COPY --chown $BUILD_USR:root -dir \
        project/build.properties \
        project/BuildSettings.scala \
        project/Common.scala \
        project/Dependencies.scala \
        project/DockerSettings.scala \
        project/plugins.sbt \
        project/Publish.scala \
        project/protoc.sbt \
        ./project/

    # clean & install dependencies
    RUN sbt clean cleanFiles update

    # copy proto definitions & generate
    COPY --chown $BUILD_USR:root -dir proto .
    RUN sbt protocGenerate

    # copy code
    COPY --chown $BUILD_USR:root code/service/src ./code/service/src
    COPY --chown $BUILD_USR:root code/plugin/src ./code/plugin/src

docker-stage:
    # package the jars/executables
    FROM +code
    # TODO: use a simpler linux packager
    # https://www.scala-sbt.org/sbt-native-packager/formats/debian.html
    RUN sbt docker:stage
    RUN chmod -R u=rX,g=rX code/service/target/docker/stage
    SAVE ARTIFACT code/service/target/docker/stage/0/opt/docker /target

docker-build:
    # bundle into a slimmer, runnable container
    FROM openjdk:11-jre-slim

    ARG VERSION=dev

    USER root

    # create cos user for the service
    RUN groupadd -r cos && useradd --gid cos -r --shell /bin/false cos

    # copy over files
    WORKDIR /opt/docker
    COPY --chown cos:root +docker-stage/target .

    # set runtime user to cos
    USER cos

    ENV JAVA_OPTS="-Xms256M -Xmx1G -XX:+UseG1GC"

    ENTRYPOINT /opt/docker/bin/entrypoint
    CMD []

    # build the image and push remotely (if all steps are successful)
    SAVE IMAGE --push namely/chief-of-state:${VERSION}


test-local:
    FROM +code
    ARG CODECOV_TOKEN=""
    ENV CODECOV_TOKEN=${CODECOV_TOKEN}
    RUN sbt coverage test coverageAggregate

codecov:
    FROM +test-local
    ARG COMMIT_HASH=""
    ARG BRANCH_NAME=""
    ARG BUILD_NUMBER=""
    RUN curl -s https://codecov.io/bash > codecov.sh && chmod +x codecov.sh
    RUN --secret CODECOV_TOKEN=+secrets/CODECOV_TOKEN \
        ./codecov.sh -t "${CODECOV_TOKEN}" -B "${BRANCH_NAME}" -C "${COMMIT_HASH}" -b "${BUILD_NUMBER}"

test-all:
    BUILD +test-local
    BUILD +codecov

sbt:
    # TODO: move this to a central image
    FROM openjdk:11-jdk-stretch

    # Install sbt
    ARG SBT_VERSION=1.3.6
    RUN \
        curl -L -o sbt.deb https://dl.bintray.com/sbt/debian/sbt-${SBT_VERSION}.deb && \
        dpkg -i sbt.deb && \
        rm sbt.deb && \
        apt-get update && \
        apt-get install sbt && \
        sbt sbtVersion

