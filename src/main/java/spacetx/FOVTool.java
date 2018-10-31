package spacetx;

import loci.common.LogbackTools;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.*;
import loci.formats.in.DynamicMetadataOptions;
import loci.formats.meta.MetadataStore;
import loci.formats.ome.OMEXMLMetadata;
import loci.formats.out.OMETiffWriter;
import loci.formats.services.OMEXMLService;
import loci.formats.tiff.IFD;
import loci.formats.tools.ImageConverter;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;


/**
 * Main entry point for SpaceTx FOV generation.
 */
public class FOVTool {

    /**
     * Set to desired logging level
     */
    private final static String LOGLEVEL = System.getProperty("spacetx.FOVTool.loglevel", "warn");

    //
    // PRIMARY ARGUMENTS
    //

    /**
     * Represents the field-of-view in the <b>output fileset</b>
     * regardless of the number of series in the input fileset.
     */
    @Option(name="-f",usage="field of view", metaVar="FOV")
    private int fov = 0;

    /**
     * Non-extant directory which should be used to contain the
     * SpaceTx output fileset.
     */
    @Option(name="-o",usage="create & output to this directory", metaVar="OUTPUT", required=true)
    private File out = new File("out");

    /**
     * Path to the codebook which should be attached to the fileset.
     * It will be copied into the output directory. If no codebook is
     * provide, then the name "codebook.json" will be used.
     */
    @Option(name="-c",usage="codebook to attach", metaVar="CODEBOOK")
    private File codebook = new File("codebook.json");

    /**
     * Naming strategies for generating the names of files on disk.
     *
     * Currently only "standard" is supported.
     */
    @Option(name="-n",usage="naming strategy ('standard')", metaVar="NAMING")
    private Naming naming = Naming.standard;

    //
    // BIO-FORMATS INTERNALS
    //

    /**
     * Represents the field-of-view in the <b>output fileset</b>
     * regardless of the number of series in the input fileset.
     */
    @Option(name="-s",usage="series offset of image", metaVar="SERIES")
    private int series = -1;

    /**
     * Primary input file to Bio-Formats. Related files will be auto-detected.
     *
     * See https://docs.openmicroscopy.org/latest/bioformats/formats/dataset-table.html
     * for more information.
     */
    @Argument(required=true, metaVar="INPUT", usage="main input file for Bio-Formats")
    private String input = null;

    public static void main(String[] args) throws Exception {
        System.exit(new FOVTool().doMain(args));
    }

    public int doMain(String[] args) throws IOException, FormatException {
        CmdLineParser parser = new CmdLineParser(this);
        parser.getProperties().withUsageWidth(80);

        try {
            parser.parseArgument(args);
            if (!new File(input).exists()) {
                throw new UsageException(1, String.format(
                        "input does not exist (%s)", input
                ));
            }
            if (out.exists()) {
                throw new UsageException(3, String.format(
                        "output folder already exists! (%s)", out));
            } else {
                out.mkdirs();
            }
            if (fov < 0) {
                throw new UsageException(5, String.format(
                        "FOV must be a greater than or equal to 0 (%d)", fov
                ));
            }
            return convert();
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            System.err.println("java spacetx.FOVTool [options...] arguments...");
            parser.printUsage(System.err);
            System.err.println();
            if (e instanceof UsageException) {
                return ((UsageException) e).rc;
            }
            return 2;
        }

    }

    /**
     * Reads the input file into an {@link ImageReader} in order to have all necessary metadata,
     * then uses {@link ImageConverter} to produce the TIFF stacks, and finally uses {@link FOVWriter}
     * to produce the necessary JSON.
     *
     * @return non-zero return code if anything went wrnog
     * @throws IOException
     * @throws FormatException
     * @throws UsageException
     */
    public int convert() throws IOException, FormatException, UsageException {

        LogbackTools.setRootLevel(LOGLEVEL);

        // First use the generic reader object to load the metadata
        DynamicMetadataOptions options = new DynamicMetadataOptions();
        options.setValidate(true);
        OMEXMLMetadata meta = null;
        try {
            OMEXMLService xml = new ServiceFactory().getInstance(OMEXMLService.class);
            meta = xml.createOMEXMLMetadata();
        } catch (ServiceException | DependencyException exc) {
            throw new FormatException("Error creating metadata service");
        }
        ImageReader reader = new ImageReader();
        reader.setMetadataOptions(options);
        reader.setGroupFiles(true);
        reader.setMetadataFiltered(true);
        reader.setOriginalMetadataPopulated(true);
        reader.setMetadataStore(meta);
        reader.setId(input);
        MetadataStore store = reader.getMetadataStore();
        // doPlane: true is critical for position information
        MetadataTools.populatePixels(store, reader, true, false);

        final int plateCount = meta.getPlateCount();
        final int seriesCount = reader.getSeriesCount();
        final ExperimentWriter writer = new ExperimentWriter(naming, out);

        if (plateCount > 0) {
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
            for (int i = 0; i < seriesCount; i++) {
                reader.setSeries(i);
                int rv = convertOne(reader, meta, writer, i);
                if (rv != 0) {
                    return rv;
                }
            }
            writer.write();
            return 0;
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
            int rv = convertOne(reader, meta, writer, fov);
            writer.write();
            return rv;
        }
    }

    private int convertOne(ImageReader reader, OMEXMLMetadata meta, ExperimentWriter eWriter, int fov)
            throws FormatException, IOException {
        String companion = String.format("%s/%s", out, naming.getCompanionFilename(fov));
        String tiffs = String.format("%s/%s", out, naming.getTiffPattern(fov));
        ImageConverter converter = createConverter();

        String[] cmd = new String[]{
                "-option", "ometiff.companion", companion,
                "-validate", input, tiffs
        };
        if (!converter.testConvert(imageWriter(), cmd)) {
            System.out.println("Conversion failed!");
            return 1;
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
    private static FormatWriter imageWriter() {
        return new OMETiffWriter() {

            int calls = 0;
            long bytes = 0;
            long elapsed = 0;

            public void saveBytes(int no, byte[] buf, IFD ifd, int x, int y, int w, int h)
                    throws IOException, FormatException {
                this.calls++;
                this.bytes += buf.length;
                long start = System.currentTimeMillis();
                try {
                    super.saveBytes(no, buf, ifd, x, y, w, h);
                } finally {
                    long stop = System.currentTimeMillis();
                    long elapsed = stop - start;
                    this.elapsed += elapsed;
                    System.out.println(String.format(
                            "> write([%04d]) to %s: %8d bytes in %4d ms (Avg. %5.3f MB/s)",
                            calls, currentId.substring(currentId.lastIndexOf(File.separatorChar)+1),
                            buf.length, elapsed, ((double) this.bytes)/this.elapsed/1000
                    ));
                }
            }
        };
    }
}
