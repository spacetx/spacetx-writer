package spacetx;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import loci.formats.ImageReader;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;

/**
 * Produces the FOV json file for a SpaceTx experiment.
 *
 * Size attributes are taken from the {@link ImageReader} and filename
 * assumptions are made based on values in {@link FOVTool}.
 */
public class FOVWriter {

    private final int sizeX, sizeY, sizeC, sizeT, sizeZ;
    private final int fov;
    private final Naming naming;
    private final File out;

    public FOVWriter(ImageReader reader, Naming naming, int fov, File out) {
        this.fov = fov;
        this.out = out;
        this.naming = naming;
        this.sizeX = reader.getSizeX();
        this.sizeY = reader.getSizeY();
        this.sizeC = reader.getSizeC(); // TODO: getEffectiveSizeC?
        this.sizeT = reader.getSizeT();
        this.sizeZ = reader.getSizeZ();
    }

    public void write() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter() {
            @Override
            public DefaultPrettyPrinter withSeparators(Separators separators) {
                super.withSeparators(separators);
                this._objectFieldValueSeparatorWithSpaces = ": ";
                return this;
            }
        };
        DefaultIndenter indenter = new DefaultIndenter("    ", DefaultIndenter.SYS_LF);
        printer.indentArraysWith(indenter);
        printer.indentObjectsWith(indenter);

        ObjectNode primary = mapper.createObjectNode();
        primary.put("default_tile_format", "TIFF");
        // "default_tile_shape" (will be used below)
        ArrayNode plane = mapper.createArrayNode();
        plane.add(sizeX);
        plane.add(sizeY);
        // "dimensions"
        ArrayNode dims = mapper.createArrayNode();
        dims.add("r");
        dims.add("x");
        dims.add("y");
        dims.add("c");
        dims.add("z");
        dims.add("xc");
        dims.add("yc");
        dims.add("zc");
        primary.set("dimensions", dims);
        // "extras"
        ObjectNode extras = mapper.createObjectNode();
        extras.put("OME", naming.getCompanionFilename(fov));
        primary.set("extras", extras);
        // "shape"
        ObjectNode shape = mapper.createObjectNode();
        shape.put("c", sizeC);
        shape.put("r", sizeT);
        shape.put("z", sizeZ);
        primary.set("shape", shape);
        // tiles
        ArrayNode tiles = mapper.createArrayNode();
        for (int z = 0; z < sizeZ; z++) {
            for (int t = 0; t < sizeT; t++) {
                for (int c = 0; c < sizeC; c++) {
                    ObjectNode tile = mapper.createObjectNode();
                    ObjectNode coords = mapper.createObjectNode();
                    for (String idx : new String[] {"xc", "yc", "zc"}) {
                        ArrayNode coord = mapper.createArrayNode();
                        coord.add(0.0);
                        coord.add(new BigDecimal(.0001).setScale(4, BigDecimal.ROUND_HALF_UP));
                        coords.set(idx, coord);
                    }
                    tile.set("coordinates", coords);
                    String file = naming.getTiffFilename(fov, z, t, c);
                    tile.put("file", file);
                    ObjectNode indices = mapper.createObjectNode();
                    indices.put("c", c);
                    indices.put("r", t);
                    indices.put("z", z);
                    tile.set("indices", indices);
                    HashCode hashCode = Files.hash(new File(out, file), Hashing.sha256());
                    tile.put("sha256", hashCode.toString());
                    tile.put("tile_format", "TIFF");
                    tile.set("tile_shape", plane);
                    tiles.add(tile);
                }
            }
        }
        primary.set("tiles", tiles);
        primary.put("version", "1.0.0");
        String name = String.format("%s/%s", out, naming.getJsonFilename(fov));
        ObjectWriter writer = mapper.writer(printer);
        writer.writeValue(new File(name), primary);

        ObjectNode manifest = mapper.createObjectNode();
        ObjectNode contents = mapper.createObjectNode();
        contents.put(naming.getFOV(fov), naming.getJsonFilename(fov));
        manifest.put("contents", contents);
        manifest.put("extras", (ObjectNode) null);
        manifest.put("version", "0.0.0");
        writer = mapper.writer(printer);
        writer.writeValue(new File(
            String.format("%s/%s", out, naming.getManifestFilename())), manifest);

        ObjectNode exp = mapper.createObjectNode();
        exp.put("version", "4.0.0");
        exp.set("auxiliary_images", mapper.createObjectNode());
        exp.set("extras", mapper.createObjectNode());
        exp.put("primary_images", naming.getManifestFilename());
        exp.put("codebook", "codebook.json");
        writer = mapper.writer(printer);
        writer.writeValue(new File(String.format("%s/experiment.json", out)), exp);

        ObjectNode book = mapper.createObjectNode();
        ArrayNode mappings = mapper.createArrayNode();
        ObjectNode code = mapper.createObjectNode();
        ArrayNode words = mapper.createArrayNode();
        ObjectNode word = mapper.createObjectNode();
        word.put("r", 0);
        word.put("c", 0);
        word.put("v", 1);
        words.add(word);
        code.put("codeword", words);
        code.put("target", "PLEASE_REPLACE_ME");
        mappings.add(code);
        book.put("version", "0.0.0");
        book.put("mappings", mappings);
        writer = mapper.writer(printer);
        writer.writeValue(new File(String.format("%s/codebook.json", out)), book);
    }

}
