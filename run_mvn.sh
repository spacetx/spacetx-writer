#!/usr/bin/env bash

DIR=/tmp/bio-formats-6.0.0-SNAPSHOT
mkdir $DIR
trap "rm -rf $DIR" EXIT

cd $DIR
git clone -b east --depth 1 https://github.com/ome/bio-formats-build
pushd bio-formats-build

export LANG=en_US.UTF-8 

git submodule update --init
mvn clean install -DskipSphinxTests
cd bioformats
ant clean jars tools test
