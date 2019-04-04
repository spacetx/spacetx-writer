# Docker build
# ------------
# This dockerfile splits the build and distribution
# of the Java code to provide a slimmed down final
# image.

# By default, building this dockerfile will use
# the IMAGE argument below for the runtime image.
ARG BUILD_IMAGE=gradle:5.2.1-jdk8

# To build code with other runtimes
# pass a build argument, e.g.:
#
#   docker build --build-arg BUILD_IMAGE=openjdk:9 ...
#

# The produced distribution will be copied to the
# RUN_IMAGE for end-use. This value can also be
# set at build time with --build-arg RUN_IMAGE=...
ARG RUN_IMAGE=openjdk:8-slim

FROM ${BUILD_IMAGE} as build
USER root
RUN useradd -ms /bin/bash build
COPY build.gradle /opt/spacetx-writer/build.gradle
RUN chown -R build /opt/spacetx-writer

# Pre-load all the jars which significantly speeds up developmnet
WORKDIR /opt/spacetx-writer
USER build
RUN env GRADLE_OPTS="-Dorg.gradle.daemon=false" gradle deps

# Copy the rest of the code
USER root
COPY src /opt/spacetx-writer/src
RUN chown -R build /opt/spacetx-writer

USER build
WORKDIR /opt/spacetx-writer
RUN env GRADLE_OPTS="-Dorg.gradle.daemon=false" gradle build

FROM ${RUN_IMAGE} as run
COPY --from=build /opt/spacetx-writer/build/distributions/*.tar /tmp

USER root
RUN  tar -C /usr/local --strip-components=1 -xvf /tmp/spacetx-writer-0.0.4-SNAPSHOT.tar \
 &&  rm /tmp/spacetx-writer*tar

RUN useradd -ms /bin/bash spacetx
USER spacetx
ENTRYPOINT ["spacetx-writer"]
