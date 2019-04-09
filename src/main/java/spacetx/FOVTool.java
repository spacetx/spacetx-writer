package spacetx;

import loci.common.LogbackTools;
import loci.formats.*;
import loci.formats.in.DynamicMetadataOptions;
import loci.formats.in.MetadataOptions;
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

    /**
     * Options to pass to Bio-Formats.
     * See https://docs.openmicroscopy.org/latest/bio-formats/formats/options.html?highlight=options
     */
    @Option(name="--options", usage="Options of the form: 'k=v:k=v'")
    private String options = null;

    /**
     * Options to pass to Bio-Formats.
     * See https://docs.openmicroscopy.org/latest/bio-formats/formats/options.html?highlight=options
     */
    @Option(name="--flags", usage="Flags of the form 'f1:f2' without hyphens")
    private String flags = null; // TODO: these won't apply to --guess

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

    IFormatReader reader;

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
                    Errors.doesNotExist.raise(input);
                }
            }

            LogbackTools.setRootLevel(LOGLEVEL);
            reader = createReader(format);
            if (format != null && reader == null) {
                Errors.unknownFormat.raise(format);
            }

            if (info) {
                try {
                    List<String> infoArgs = new ArrayList<>();
                    infoArgs.add("-nopix");
                    infoArgs.add("-cache"); // TODO: should match options
                    addOptions(infoArgs);
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
                addOptions(stitcher); // TODO: perhaps not necessary
                stitcher.setId(inputs.get(0));
                String content = stitcher.getFilePattern().getPattern();

                if (out == null) {
                    System.out.println(content);
                } else {
                    if (!out.getAbsolutePath().endsWith(".pattern")) {
                        Errors.patternFiles.raise();
                    }
                    out.getParentFile().mkdirs();
                    Files.write(out.toPath(), content.getBytes());
                    System.out.println(String.format("Wrote %s to %s", content, out));
                }
                return 0;

            }

            if (out == null) {
                Errors.needAction.raise();
            } else if (out.exists()) {
                Errors.outputExists.raise(out);
            } else {
                out.mkdirs();
            }

            if (fov < 0) {
                Errors.fovIsPositive.raise(fov);
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
                if (t instanceof Errors.UsageException) {
                    copy = (Errors.UsageException) t;
                } else {
                    copy.printStackTrace(); // These are hard to debug, so show the user the verbiage.
                }
            }

            System.err.println("spacetx-writer [options...] arguments...");
            parser.printUsage(System.err);
            System.err.println();

            int rc = Errors.usage.rc;
            if (copy instanceof Errors.UsageException) {
                rc = ((Errors.UsageException) copy).rc;
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
            if (reader != null) {
                reader.close();
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
     * @throws Errors.UsageException
     */
    public int convert(FOVParser parser, ExperimentWriter writer, int loop)
            throws IOException, FormatException, Errors.UsageException {
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
                Errors.singleScreening.raise();
            }

            // We assume that a HCS dataset it more structured, having the same
            // coordinate system for all the wells, therefore we can remove some
            // of the restrictions around choosing FOV.
            if (plateCount > 1) {
                // however, to allow fov to choose the _well_, we abort if there
                // are more than one plate.
                Errors.tooManyPlates.raise(plateCount);
            }

            int wellCount = meta.getWellCount(0);
            if (wellCount != 1) {
                Errors.tooManyWells.raise(wellCount);
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
                    Errors.multipleImages.raise(input, reader.getSeriesCount());
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

        List<String> cmd = new ArrayList<>();
        addOptions(cmd);
        cmd.add("-series");
        cmd.add(String.valueOf(reader.getSeries()));
        cmd.add("-option");
        cmd.add("ometiff.companion");
        cmd.add(companion);
        cmd.add("-validate");
        cmd.add(input);
        cmd.add(tiffs);
        if (!noTiffs) {
            try (FormatWriter writer = imageWriter()) {
                if (!converter.testConvert(writer, cmd.stream().toArray(String[]::new))) {
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
    private IFormatReader createReader(String format) throws Errors.UsageException {

        IFormatReader reader = null;
        if (format == null) {
            reader = new ImageReader();
        } else {
            try {
                Class c = Class.forName(String.format(String.format("loci.formats.in.%sReader", format)));
                reader = (IFormatReader) c.newInstance();
            } catch (Exception e) {
                try {
                    Class c = Class.forName(String.format(format));
                    reader = (IFormatReader) c.newInstance();
                } catch (Exception e2) {
                    return null;  // EARLY EXIT!
                }
            }
        }
        reader = new Memoizer(reader);
        addOptions(reader);
        return reader;
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

    /**
     * Add each option to the given arguments list.
     */
    private void addOptions(List<String> args) {
        if (options != null) {
            for (String option : options.split("[:;]")) {
                args.add("-option");
                for (String kOrV : option.split("=")) {
                    args.add(kOrV);
                }
            }
        }
        if (flags != null) {
            for (String flag : flags.split("[:;]")) {
                args.add("-"+flag);
            }
        }
    }

    /**
     * Add each option to the given reader's metadata options.
     */
    private void addOptions(IFormatReader reader) throws Errors.UsageException {
        if (options != null) {
            DynamicMetadataOptions opts = (DynamicMetadataOptions) reader.getMetadataOptions();
            for (String option : options.split("[:;]")) {
                String[] kv = option.split("=");
                if (kv.length != 2) {
                    throw Errors.badOption.raise(option);
                } else {
                    opts.set(kv[0], kv[1]);
                }
            }
        }
    }
}
