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

    gradle

Downloaded files (including the Gradle distribution itself) will be stored in
the Gradle user home directory (`~/.gradle` by default).

Building with Docker
--------------------

From the root directory, run:

    docker build -t sptx .

Running the built image will call the `spacetx-fov-writer` executable
by default.

Usage
-----

    gradle run --args "my-data.nd2 -o new-directory"
    spacetx-fov-writer -o new-directory my-data.nd2
    docker run -ti --rm -v /tmp:/tmp -v /data:/data sptx -o /tmp/new-directory /data/my-data.nd2

Further Resources
-----------------

- [SpaceTx format specification](https://github.com/spacetx/sptx-format)
- [Starfish library](https://github.com/spacetx/starfish)
- [Bio-Formats homepage](https://www.openmicroscopy.org/bio-formats)
- [Bio-Formats documentation](https://docs.openmicroscopy.org/latest/bio-formats/)
