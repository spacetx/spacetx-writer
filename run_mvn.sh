#!/usr/bin/env bash

set -e
set -u

DIR=/tmp/bio-formats-6.0.0-SNAPSHOT
mkdir $DIR
trap "rm -rf $DIR" EXIT

cd $DIR
# Requires exclusion of S3
git clone -b east_merge_trigger --depth 1 https://github.com/snoopycrimecop/bio-formats-build
pushd bio-formats-build

export LANG=en_US.UTF-8 

git submodule update --init
mvn clean install -DskipSphinxTests
cd bioformats
ant tools
