SpaceTx Writer
==============

Command-line tool and library for converting bioimaging filesets
into the SpaceTx format. Generated filesets contain the required
SpaceTx metadata in JSON, 2D TIFF stacks, and an OME companion
file.

 * [Getting started](#getting-started)
 * [Usage](#usage)

Getting started
---------------

### Prerequisites

The following are required:

    JDK 7 or higher
    Gradle

### Building with Gradle

From the root directory, run:

    gradle build

Downloaded files (including the Gradle distribution itself) will be stored in
the Gradle user home directory (`~/.gradle` by default).

### Installing Gradle build

You will need to unpack one of the built distribution from `build/distributions`, e.g.:

    unzip -d /tmp/ build/distributions/spacetx-writer-$VERSION.zip
    /tmp/spacetx-writer-$VERSION/bin/spacetx-writer

### Building with Docker

If instead of installing a JDK and building from source, you can locally build the docker image:
From the root directory, run:

    docker build -t spacetx-writer .

Running the built image will call the `spacetx-writer` executable by default.

### Pulling from GitLab

Finally, you can pull a pre-built image from Docker Hub:

    docker pull spacetx/spacetx-writer

Usage
-----

### Basics

If you would like to use the docker image, it is important that you mount input and output
directories into the container:

    docker run -ti --rm -v /tmp:/tmp -v /data:/data:ro spacetx-fov-writer ...

Once that is done, behavior of the docker container and the command-line are the same. A
simplest invocation would be:

    spacetx-fov-writer -o /tmp/new-directory /data/my-data.nd2

See `-h` for more information.

### Choosing your input file

Bio-Formats takes a single input file, searches through around 150 different file formats
to determine the type, and then groups associated files together into a single fileset
automatically. You can see a list of the file formats and which file should be chosen
on the [Datset Structure Table](https://docs.openmicroscopy.org/bio-formats/6.0.1/formats/dataset-table.html)
page.

### Grouping files

If Bio-Formats does not detect the data type, you may need to tell it how to group multiple
multiple unrelated files into a single fileset. This can be done by creating a "pattern file".

For example, if you have 5 large TIFF files t1.tif through t5.tif, each representing a separate round,
create a file with the contents:

    t<1-5>.tif

and pass it to the tool. Similarly, if you have several 2D TIFFs, create a file which matches
your chosen pattern:

    my_tiffs_z<1-12>_c<1-3>_t<1-100>.tiff

For more information, see the
[Grouping files using a pattern file](https://docs.openmicroscopy.org/bio-formats/6.0.1/formats/pattern-file.html)
page on the Bio-Formats documentation.


### Multiple FOVs

If you pass multiple files to the tool, they will be interpreted as _separate_ filesets, each
of which which will be made into a field-of-view. The FOV will have the dimensions detected by Bio-Formats.
If Bio-Formats does not show the expected dimensions, you may need to try [grouping files](#grouping-files).

Further Resources
-----------------

- [SpaceTx format specification](https://github.com/spacetx/sptx-format)
- [Starfish library](https://github.com/spacetx/starfish)
- [Bio-Formats homepage](https://www.openmicroscopy.org/bio-formats)
- [Bio-Formats documentation](https://docs.openmicroscopy.org/bio-formats/6.0.1)
