package spacetx.tests;


import com.google.common.collect.ImmutableMap;
import loci.common.LogbackTools;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import spacetx.FOVTool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static spacetx.tests.Helpers.*;

/**
 * Simulate use from the CLI. Bad arguments should have a non-zero return code.
 */
public class ToolTest {

    /**
     * Set to false during development in order to leave directories under $TMPDIR
     */
    private final static boolean cleanup = Boolean.valueOf(
            System.getProperty("spacetx.tests.ToolTest.cleanup", "true"));

    /**
     * Simple testing input that is created by touching a file with an appropriately constructed name.
     * See https://docs.openmicroscopy.org/latest/bio-formats/developers/generating-test-images.html
     */
    private Path fake;

    /**
     * Output directory under $TMPDIR
     */
    private Path dir;

    /**
     * Instance under test.
     */
    private FOVTool tool;

    /**
     * Run a the FOVTool main method and check for success or failure.
     *
     * @param exitCode 0 for success
     * @param additionalArgs CLI arguments as needed beyond "-o output input"
     */
    void assertTool(int exitCode, String...additionalArgs) {
        List<String> args = new ArrayList<String>();
        args.add("-o");
        args.add(dir.toString());
        args.add(fake.toString());
        for (String arg : additionalArgs) {
            args.add(arg);
        }
        try {
            Assertions.assertEquals(exitCode, tool.doMain(args.toArray(new String[]{})));
        } catch (RuntimeException rt) {
            throw rt;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Fill in the necessary instance variables. Note: fake will need
     * to be created by all tests.
     *
     * @throws Exception Standard Java IO errors only.
     */
    @BeforeEach
    public void setup() throws Exception {
        LogbackTools.setRootLevel("error");
        tool = new FOVTool();
        dir = Files.createTempDirectory("ToolTest");
        dir.toFile().delete(); // Delete initially created directory.
        if (cleanup) {
            dir.toFile().deleteOnExit(); // Cleanup if it gets recreated.
        }
    }

    @Test
    public void testInputDoesntExist() {
        fake = fake();
        fake.toFile().delete();
        assertTool(1);
    }

    @Test
    public void testOutputExists() {
        fake = fake();
        dir.toFile().mkdirs();
        assertTool(3);
    }

    @Test
    public void testInputHasMultipleSeriesNoChoice() {
        fake = fake("series", "2");
        assertTool(4);
    }

    @Test
    public void testInputHasMultipleSeriesWithChoice() {
        fake = fake("series", "2");
        assertTool(0, "-s", "0");
    }

    @Test
    public void test5DImage() {
        fake = fake("sizeZ", "5", "sizeT", "4", "sizeC", "3");
        assertTool(0);
        Assertions.assertEquals(1, matches("fov_000_Z4_T3_C2.ome.tiff", dir));

    }

    @Test
    public void testNegativeFOV() {
        fake = fake();
        assertTool(5, "-f", "-1");
    }

    @Test
    public void testNonDefaultFOV() {
        fake = fake();
        assertTool(0, "-f", "001");
        Assertions.assertEquals(1, matches("fov_001_Z0_T0_C0.ome.tiff", dir));
    }

    @Test
    public void testHCSFailsOnTooManyPlates() {
        fake = fake("plates", "2");
        assertTool(6);
    }

    @Test
    public void testHCSFailsOnTooManyWells() {
        fake = fake("plates", "1", "plateRows", "2");
        assertTool(7);
    }

    @Test
    public void testHCSAllFieldsOfOneWell() {
        fake = fake("plates", "1", "fields", "2");
        assertTool(0);
        Assertions.assertEquals(1, matches("fov_000_Z0_T0_C0.ome.tiff", dir));
        Assertions.assertEquals(1, matches("fov_001_Z0_T0_C0.ome.tiff", dir));
        Assertions.assertEquals(1, matches("codebook.json", dir));
        Assertions.assertEquals(1, matches("experiment.json", dir));
        Assertions.assertEquals(1, matches("primary_image-fov.json", dir));
    }

    @Test
    public void testHCSPositionOfField() throws Exception {
        fake = fake(
                ImmutableMap.<String, String>builder().put("plate", "1").build(),
                ImmutableMap.<Integer, Map<String, String>>builder().put(0,
                    ImmutableMap.<String, String>builder()
                            .put("PositionX_0", "444")
                            .put("PositionY_0", "555").build()).build());
        assertTool(0);

        // Now check for the position values
        Assertions.assertEquals(1,
                grep("primary_image-fov_000.companion.ome", "PositionX=\"444.0\"", dir));
        Assertions.assertEquals(1,
                grep("primary_image-fov_000.json", "444", dir));
    }

    /**
     * Delete the created resources under $TMPDIR unless cleanup was set to false.
     */
    @AfterEach
    public void teardown() {
        fake.toFile().delete();
        if (cleanup) {
            dir.toFile().delete();
        } else {
            System.out.println("Not deleting output directory:");
            System.out.println(dir);
        }
    }

}
