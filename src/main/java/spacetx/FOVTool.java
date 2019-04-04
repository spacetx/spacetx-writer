package spacetx;

import loci.common.LogbackTools;
import loci.formats.*;
import loci.formats.ome.OMEXMLMetadata;
import loci.formats.out.OMETiffWriter;
import loci.formats.tiff.IFD;
import loci.formats.tools.ImageConverter;
import loci.formats.tools.ImageInfo;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/**
 * Main entry point for SpaceTx FOV generation.
 */
public class FOVTool {

    /**
     * Set to desired logging level
     */
    private final static String LOGLEVEL = System.getProperty("spacetx.FOVTool.loglevel", "warn");

    //
    // PRIMARY INPUT ARGUMENTS
    //

    /**
     * Path to the codebook which should be attached to the fileset.
     * It will be copied into the output directory. If no codebook is
     * provided, then the name "codebook.json" will be used.
     */
    @Option(name="-c", usage="codebook to attach", metaVar="CODEBOOK")
    private File codebook = new File("codebook.json");

    /**
     * Represents the first field-of-view in the <b>output fileset</b>
     * regardless of the number of series in the input filesets.
     */
    @Option(name="-f", usage="field of view", metaVar="FOV")
    private int fov = 0;

    @Option(name="-j", usage="concurrent threads", metaVar="THREADS")
    private int threads = 1;

    //
    // PRIMARY OUTPUT ARGUMENTS
    //

    /**
     * Non-extant directory which should be used to contain the
     * SpaceTx output fileset.
     */
    @Option(name="-o", usage="create & output to this directory", metaVar="OUTPUT")
    private File out = null;

    @Option(name="--guess", usage="guess a pattern file")
    private boolean guess = false;

    @Option(name="--info", usage="print information about the fileset and exit")
    private boolean info = false;

    //
    // ADVANCED ARGUMENTS
    //

    /**
     * Naming strategies for generating the names of files on disk.
     *
     * Currently only "standard" is supported.
     */
    @Option(name="-n", usage="naming strategy ('standard')", metaVar="NAMING")
    private Naming naming = Naming.standard;

    /**
     * Whether to skip generation of the OME-TIFFs and only produce the
     * starfish json.
     */
    @Option(name="--no-tiffs", usage="skip generation of OME-TIFFs")
    private boolean noTiffs = false;

    //
    // BIO-FORMATS INTERNALS
    //

    /**
     * Chooses which series from given filesets will be included in the field-of-view.
     */
    @Option(name="-s", usage="adv: series offset of image", metaVar="SERIES")
    private int series = -1;

    /**
     *
     */
    @Option(name="--format", usage="adv: specify a specific filetype", metaVar="FORMAT")
    private String format = null;

    /**
     * Primary input files to Bio-Formats. Related files will be auto-detected.
     *
     * See https://docs.openmicroscopy.org/latest/bioformats/formats/dataset-table.html
     * for more information.
     */
    @Argument(required=true, metaVar="INPUT", usage="main input file for Bio-Formats")
    private List<String> inputs = null;

    //
    // STATISTICS
    //

    /**
     * Number of calls to write TIFFs
     */
    int calls = 0;

    /**
     * Number of bytes written to TIFFs
     */
    long bytes = 0;

    /**
     * Total time spent writing TIFFs
     */
    long elapsed = 0;

    //
    // GLOBAL PARALLEL STATE
    //

    ExperimentWriter writer;

    ExecutorService executor;

    ExecutorCompletionService<Integer> ecs;

    Queue<Future<Integer>> futures;

    public static void main(String[] args) throws Exception {
        System.exit(new FOVTool().doMain(args));
    }

    public int doMain(String[] args) throws IOException, FormatException {
        CmdLineParser parser = new CmdLineParser(this);
        parser.getProperties().withUsageWidth(80);

        try {
            parser.parseArgument(args);
            executor = Executors.newFixedThreadPool(threads);
            for (String input : inputs) {
                if (!new File(input).exists()) {
                    throw new UsageException(1, String.format(
                            "input does not exist (%s)", input
                    ));
                }
            }

            LogbackTools.setRootLevel(LOGLEVEL);
            IFormatReader reader = createReader(format);
            if (format != null && reader == null) {
                throw new UsageException(11, String.format(
                    "unknown format: %s", format
                ));
            }

            if (info) {
                try {
                    List<String> infoArgs = new ArrayList<>();
                    if (format != null) {
                        infoArgs.add("-format");
                        infoArgs.add(format);
                    }
                    infoArgs.add(inputs.get(0));
                    ImageInfo.main(infoArgs.stream().toArray(String[]::new));
                    return 0;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            if (guess) {
                // In the guess scenario, we don't want to create an output
                // directory, but a single file which will be the input to
                // a new execution. (i.e. EXIT EARLY)

                inputs.sort(Comparator.naturalOrder());
                FileStitcher stitcher = new FileStitcher(reader);
                stitcher.setId(inputs.get(0));
                String content = stitcher.getFilePattern().getPattern();

                if (out == null) {
                    System.out.println(content);
                } else {
                    if (!out.getAbsolutePath().endsWith(".pattern")) {
                        throw new UsageException(9, String.format(
                                "pattern files must end in '.pattern'"
                        ));
                    }
                    out.getParentFile().mkdirs();
                    Files.write(out.toPath(), content.getBytes());
                    System.out.println(String.format("Wrote %s to %s", content, out));
                }
                return 0;

            }

            if (out == null) {
                throw new UsageException(10, "one of --output, --info, --guess required");
            } else if (out.exists()) {
                throw new UsageException(3, String.format(
                        "output location already exists! (%s)", out));
            } else {
                out.mkdirs();
            }

            if (fov < 0) {
                throw new UsageException(5, String.format(
                        "FOV must be a greater than or equal to 0 (%d)", fov
                ));
            }

            int loop = 0;
            int rv = 0;
            writer = new ExperimentWriter(naming, out);
            ecs = new ExecutorCompletionService<>(executor);
            futures = new ConcurrentLinkedQueue<>();
            for (String input : inputs) {
                final int inner = loop++;
                futures.add(ecs.submit(() -> {
                            FOVParser fovParser = new FOVParser(createReader(format), input);
                            try {
                                return convert(fovParser, writer, inner);
                            } finally {
                                try {
                                    writer.write();
                                } finally {
                                    fovParser.close();
                                }
                            }
                        }
                ));
            }
            for (Future<Integer> future : futures) {
                rv += future.get();
            }
            return rv;

        } catch (CmdLineException | InterruptedException | ExecutionException hide) {
            Exception copy = hide;
            if (copy instanceof ExecutionException) {
                Throwable t = copy.getCause();
                if (t instanceof UsageException) {
                    copy = (UsageException) t;
                } else {
                    copy.printStackTrace(); // These are hard to debug, so show the user the verbiage.
                }
            }

            System.err.println("spacetx-writer [options...] arguments...");
            parser.printUsage(System.err);
            System.err.println();

            int rc = 2;
            if (copy instanceof UsageException) {
                rc = ((UsageException) copy).rc;
            }
            String line = String.join("", Collections.nCopies(60, "="));
            System.err.println(line);
            System.err.println(String.format(
                    "ERROR(%s): %s", rc, copy.getMessage()));
            System.err.println(line);
            return rc;
        } finally {
            if (executor != null) {
                executor.shutdownNow();
            }
        }

    }

    /**
     * Reads an input file into a {@link ImageReader} in order to have all necessary metadata,
     * then uses {@link ImageConverter} to produce the TIFF stacks, and finally uses {@link FOVWriter}
     * to produce the necessary JSON.
     *
     * @return non-zero return code if anything went wrnog
     * @throws IOException
     * @throws FormatException
     * @throws UsageException
     */
    public int convert(FOVParser parser, ExperimentWriter writer, int loop)
            throws IOException, FormatException, UsageException {
        int rv = 0;
        String input = parser.getInput();
        int plateCount = parser.getPlateCount();
        int seriesCount = parser.getSeriesCount();
        IFormatReader reader = parser.getReader();
        OMEXMLMetadata meta = parser.getMetadata();

        if (plateCount > 0) {
            if (inputs.size() > 1) {
                // slightly unattractive (and late) way of detecting screening data
                // but this prevents us from needing to load data more than once and/or
                // keep all data in memory.
                throw new UsageException(8, "only a single screening fileset is supported");
            }

            // We assume that a HCS dataset it more structured, having the same
            // coordinate system for all the wells, therefore we can remove some
            // of the restrictions around choosing FOV.
            if (plateCount > 1) {
                // however, to allow fov to choose the _well_, we abort if there
                // are more than one plate.
                throw new UsageException(6, String.format(
                        "Too many plates found (count=%d)", plateCount));
            }

            int wellCount = meta.getWellCount(0);
            if (wellCount != 1) {
                throw new UsageException(7, String.format(
                        "Too many wells found (count=%d)", wellCount));
            }

            // This counting loop will need to be updated when/if multiple SPWs are supported
            for (int i = 0; i < seriesCount; i++) {
                if (threads <= 1) {
                    reader.setSeries(i);
                    rv += convertOne(reader, meta, input, writer, i + fov);
                    if (rv != 0) {
                        return rv;
                    }
                } else {
                    // Only parallelizing in the SPW case if required due to memory constraints.
                    final int inner = i;
                    futures.add(ecs.submit(() -> {
                                FOVParser fovParser = new FOVParser(createReader(format), input);
                                fovParser.getReader().setSeries(inner);
                                try {
                                    return convertOne(fovParser.getReader(), meta, input, writer,inner+fov);
                                } finally {
                                    try {
                                        writer.write();
                                    } finally {
                                        fovParser.close();
                                    }
                                }
                            }
                    ));
                }
            }
        } else {
            if (seriesCount > 1) {
                if (series < 0) {
                    // User didn't choose a series
                    throw new UsageException(4,
                            String.format("%s contains multiple images (count=%d). Please choose one.",
                                    input, reader.getSeriesCount())
                    );
                } else {
                    reader.setSeries(series);
                }
            }
            rv += convertOne(reader, meta, input, writer, loop+fov);
        }
        return rv;
    }

    private int convertOne(IFormatReader reader, OMEXMLMetadata meta, String input, ExperimentWriter eWriter, int fov)
            throws FormatException, IOException {
        String companion = String.format("%s/%s", out, naming.getCompanionFilename(fov));
        String tiffs = String.format("%s/%s", out, naming.getTiffPattern(fov));
        ImageConverter converter = createConverter();

        String[] cmd = new String[]{
                "-series", String.valueOf(reader.getSeries()),
                "-option", "ometiff.companion", companion,
                "-validate", input, tiffs
        };
        if (!noTiffs) {
            try (FormatWriter writer = imageWriter()) {
                if (!converter.testConvert(writer, cmd)) {
                    System.out.println("Conversion failed!");
                    return 1;
                }
            }
        }


        // Now write out the spacetx json
        FOVWriter writer = new FOVWriter(reader, meta, naming, fov, out);
        writer.write();
        eWriter.addFOV(fov);
        return 0;
    }

    /*
     * Deals with the fact that the ImageConverter constructor is package-private.
     */
    private static ImageConverter createConverter() {
        // TODO: this should be pushed upstream
        try {
            Constructor<ImageConverter> c = ImageConverter.class.getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Something's weird in Java land", e);
        }
    }

    /**
     * If no format is passed, return an {@link ImageReader}. Otherwise, try to
     * create an instance of the given format, first be prepending "loci.formats.in"
     * and appending "Reader" and then by simply looking up the class. If no such
     * class is found, return null.
     *
     * @param format possibly null
     * @return possibly null {@link IFormatReader}
     */
    private static IFormatReader createReader(String format) {

        if (format == null) {
            return new ImageReader();
        }

        try {
            Class c = Class.forName(String.format(String.format("loci.formats.in.%sReader", format)));
            return (IFormatReader) c.newInstance();
        } catch (Exception e) {
            try {
                Class c = Class.forName(String.format(format));
                return (IFormatReader) c.newInstance();
            } catch (Exception e2) {
                return null;
            }
        }

    }

    /*
     * Allows passing a return code for CLI failures.
     */
    private static class UsageException extends CmdLineException {

        final int rc;

        UsageException(int rc, String message) {
            super(message);
            this.rc = rc;
        }
    }

    /**
     * Create a {@link FormatWriter} instance which will output status updates
     * as TIFFs are saved.
     *
     * @return instance to be used by the {@link ImageConverter}. Never null.
     */
    private FormatWriter imageWriter() {
        final FOVTool tool = this;
        return new OMETiffWriter() {

            public void saveBytes(int no, byte[] buf, IFD ifd, int x, int y, int w, int h)
                    throws IOException, FormatException {
                tool.calls++;
                tool.bytes += buf.length;
                long start = System.currentTimeMillis();
                try {
                    super.saveBytes(no, buf, ifd, x, y, w, h);
                } finally {
                    long stop = System.currentTimeMillis();
                    long elapsed = stop - start;
                    tool.elapsed += elapsed;
                    System.out.println(String.format(
                            "[%04d]\t%s\t%s\t%8d bytes\t%4d ms\t    Avg. %5.3f MB/s",
                            calls, new Date(), currentId.substring(currentId.lastIndexOf(File.separatorChar)+1),
                            buf.length, elapsed, ((double) tool.bytes)/tool.elapsed/1000
                    ));
                }
            }
        };
    }
}
