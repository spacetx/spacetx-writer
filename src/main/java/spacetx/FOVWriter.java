package spacetx;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
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
        //mapper.enable(SerializationFeature.INDENT_OUTPUT);
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        DefaultIndenter indenter = new DefaultIndenter();
        printer.indentArraysWith(indenter);
        printer.indentObjectsWith(indenter);

        ObjectNode hyb = mapper.createObjectNode();
        hyb.put("default_tile_format", "TIFF");
        // "default_tile_shape"
        ArrayNode plane = mapper.createArrayNode();
        plane.add(sizeX);
        plane.add(sizeY);
        hyb.set("default_tile_shape", plane);
        // "dimensions"
        ArrayNode dims = mapper.createArrayNode();
        dims.add("x");
        dims.add("c");
        dims.add("z");
        dims.add("r");
        dims.add("y");
        hyb.set("dimensions", dims);
        // "shape"
        ObjectNode shape = mapper.createObjectNode();
        shape.put("c", sizeC);
        shape.put("r", sizeT);
        shape.put("z", sizeZ);
        hyb.set("shape", shape);
        // tiles
        ArrayNode tiles = mapper.createArrayNode();
        for (int c = 0; c < sizeC; c++) {
            for (int t = 0; t < sizeT; t++) {
                for (int z = 0; z < sizeZ; z++) {
                    ObjectNode tile = mapper.createObjectNode();
                    // TODO: coordinates
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
        hyb.set("tiles", tiles);
        hyb.put("version", "0.0.0");
        String name = String.format("%s/%s", out, naming.getJsonFilename(fov));
        ObjectWriter writer = mapper.writer(printer);
        writer.writeValue(new File(name), hyb);
    }

}
