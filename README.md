SpaceTx FOV Writer
==================

Command-line tool and library for converting bioimaging filesets
into the SpaceTx format. Generated filesets contain the required
SpaceTx metadata in JSON, 2D TIFF stacks, and an OME companion
file.

Prerequisites
-------------

The following are required:

    JDK 7 or higher
    Gradle

Building with Gradle
--------------------

From the root directory, run:

    gradle build

Downloaded files (including the Gradle distribution itself) will be stored in
the Gradle user home directory (`~/.gradle` by default).

Installing Gradle build
-----------------------

You will need to unpack one of the built distribution from `build/distributions`, e.g.:

    unzip -d /tmp/ build/distributions/spacetx-fov-writer-$VERSION.zip
    /tmp/spacetx-fov-writer-$VERSION/bin/spacetx-fov-writer

Building with Docker
--------------------

If instead of installing a JDK and building from source, you can locally build the docker image:
From the root directory, run:

    docker build -t spacetx-fov-writer .

Running the built image will call the `spacetx-fov-writer` executable by default.

Pulling from GitLab
-------------------

Finally, you can pull a pre-built image from GitLab:

    docker pull registry.gitlab.com/openmicroscopy/incubator/spacetx-fov-writer:$TAG

Usage
-----

If you would like to use the docker image, it is important that you mount input and output
directories into the container:

    docker run -ti --rm -v /tmp:/tmp -v /data:/data:ro spacetx-fov-writer ...

Once that is done, behavior of the docker container and the command-line are the same. A
simplest invocation would be:

    spacetx-fov-writer -o /tmp/new-directory /data/my-data.nd2

See `-h` for more information.

Further Resources
-----------------

- [SpaceTx format specification](https://github.com/spacetx/sptx-format)
- [Starfish library](https://github.com/spacetx/starfish)
- [Bio-Formats homepage](https://www.openmicroscopy.org/bio-formats)
- [Bio-Formats documentation](https://docs.openmicroscopy.org/latest/bio-formats/)
