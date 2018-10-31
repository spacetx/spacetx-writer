package spacetx;

import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import loci.formats.ImageReader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Produces the FOV json file for a SpaceTx experiment.
 *
 * Size attributes are taken from the {@link ImageReader} and filename
 * assumptions are made based on values in {@link FOVTool}.
 */
public class ExperimentWriter {

    private final Naming naming;
    private final File out;
    private List<Integer> fovs = new ArrayList<Integer>();

    public ExperimentWriter(Naming naming, File out) {
        this.out = out;
        this.naming = naming;
    }

    public void addFOV(int i) {
        fovs.add(i);
    }

    public void write() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        PrettyPrinter printer = naming.createPrinter();
        ObjectWriter writer = mapper.writer(printer);

        ObjectNode manifest = mapper.createObjectNode();
        ObjectNode contents = mapper.createObjectNode();
        for (Integer fov : fovs) {
            contents.put(naming.getFOV(fov),
                    naming.getJsonFilename(fov));
        }
        manifest.set("contents", contents);
        manifest.set("extras", null);
        manifest.put("version", "0.0.0");
        writer.writeValue(new File(
            String.format("%s/%s", out, naming.getManifestFilename())), manifest);

        ObjectNode exp = mapper.createObjectNode();
        ObjectNode images = mapper.createObjectNode();
        exp.put("version", "5.0.0");
        exp.set("extras", mapper.createObjectNode());
        exp.set("images", images);
        exp.put("codebook", "codebook.json");
        images.put("primary", naming.getManifestFilename());

        writer.writeValue(new File(String.format("%s/experiment.json", out)), exp);

        ArrayNode book = mapper.createArrayNode();
        ObjectNode code = mapper.createObjectNode();
        ArrayNode words = mapper.createArrayNode();
        ObjectNode word = mapper.createObjectNode();
        word.put("r", 0);
        word.put("c", 0);
        word.put("v", 1);
        words.add(word);
        code.put("codeword", words);
        code.put("target", "PLEASE_REPLACE_ME");
        book.add(code);
        writer.writeValue(new File(String.format("%s/codebook.json", out)), book);
    }

}
