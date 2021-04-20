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

    USER root

    RUN mkdir $BUILD_DIR && \
        chmod 777 /$BUILD_DIR

    WORKDIR $BUILD_DIR

    # copy configurations
    COPY .scalafmt.conf build.sbt .
    COPY -dir project .

    # clean & install dependencies
    RUN sbt clean cleanFiles update

    # copy proto definitions & generate
    COPY -dir proto .
    RUN sbt protocGenerate

    # copy code
    COPY -dir code .

docker-stage:
    # package the jars/executables
    FROM +code
    RUN sbt stage
    RUN chmod -R u=rX,g=rX target/universal/stage
    SAVE ARTIFACT target/universal/stage/ /target

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

    ENTRYPOINT /opt/docker/bin/entrypoint
    CMD []

    # build the image and push remotely (if all steps are successful)
    SAVE IMAGE --push namely/chief-of-state:${VERSION}

test-local:
    FROM +code
    # run with docker to enable testcontainers
    WITH DOCKER --pull postgres
        RUN sbt coverage test coverageAggregate
    END


codecov:
    FROM +test-local
    ARG COMMIT_HASH=""
    ARG BRANCH_NAME=""
    ARG BUILD_NUMBER=""
    RUN curl -s https://codecov.io/bash > codecov.sh && chmod +x codecov.sh
    RUN ./codecov.sh -B "${BRANCH_NAME}" -C "${COMMIT_HASH}" -b "${BUILD_NUMBER}"

test-all:
    BUILD +test-local
    BUILD +codecov

sbt:
    # TODO: move this to a central image
    FROM openjdk:11-jdk-stretch

    # Install sbt
    ARG SBT_VERSION=1.5.0
    ARG USER_ID=1001
    ARG GROUP_ID=1001

    # Install sbt
    RUN \
      curl -fsL "https://github.com/sbt/sbt/releases/download/v$SBT_VERSION/sbt-$SBT_VERSION.tgz" | tar xfz - -C /usr/share && \
      chown -R root:root /usr/share/sbt && \
      chmod -R 755 /usr/share/sbt && \
      ln -s /usr/share/sbt/bin/sbt /usr/local/bin/sbt

    # Add and use user sbtuser
    RUN groupadd --gid $GROUP_ID sbtuser && useradd --gid $GROUP_ID --uid $USER_ID sbtuser --shell /bin/bash
    RUN chown -R sbtuser:sbtuser /opt
    RUN mkdir /home/sbtuser && chown -R sbtuser:sbtuser /home/sbtuser
    RUN mkdir /logs && chown -R sbtuser:sbtuser /logs
    USER sbtuser

    # Switch working directory
    WORKDIR /home/sbtuser

    # This triggers a bunch of useful downloads.
    RUN sbt sbtVersion

    USER root
    RUN \
      mkdir -p /home/sbtuser/.ivy2 && \
      chown -R sbtuser:sbtuser /home/sbtuser/.ivy2 && \
      ln -s /home/sbtuser/.cache /root/.cache && \
      ln -s /home/sbtuser/.ivy2 /root/.ivy2 && \
      ln -s /home/sbtuser/.sbt /root/.sbt

    # Switch working directory back to root
    WORKDIR /root

    # install docker tools
    # https://docs.docker.com/engine/install/debian/
    RUN apt-get remove -y docker docker-engine docker.io containerd runc || true

    RUN apt-get update

    RUN apt-get install -y \
        apt-transport-https \
        ca-certificates \
        curl \
        gnupg-agent \
        software-properties-common

    RUN curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg

    RUN echo \
        "deb [arch=amd64 signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/debian \
        $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null

    RUN apt-get update
    RUN apt-get install -y docker-ce docker-ce-cli containerd.io
