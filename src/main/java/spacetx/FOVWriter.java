package spacetx;

import com.fasterxml.jackson.core.PrettyPrinter;
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
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.ome.OMEXMLMetadata;
import ome.units.UNITS;
import ome.units.quantity.Length;

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

    private final IFormatReader reader;
    private final OMEXMLMetadata meta;
    private final int sizeX, sizeY, sizeC, sizeT, sizeZ;
    private final int fov;
    private final Naming naming;
    private final File out;

    public FOVWriter(IFormatReader reader, OMEXMLMetadata meta, Naming naming, int fov, File out) {
        this.reader = reader;
        this.meta = meta;
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
        PrettyPrinter printer = naming.createPrinter();
        ObjectWriter writer = mapper.writer(printer);

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
                    for (String idx : new String[]{"xc", "yc", "zc"}) {
                        ArrayNode coord = mapper.createArrayNode();
                        Double value = getPosition(idx, z, c, t);
                        // TODO: duplication of value is due to https://github.com/spacetx/slicedimage/pull/75
                        if (value != null) {
                            coord.add(value);
                            coord.add(value);
                        } else {
                            BigDecimal dummy = new BigDecimal(0.0).setScale(4, BigDecimal.ROUND_HALF_UP);
                            coord.add(dummy);
                            coord.add(dummy);
                        }
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
                    File toHash = new File(out, file);
                    String hashString = "does-not-exist";  // in case of --no-tiffs
                    if (toHash.exists()) {
                        HashCode hashCode = Files.hash(toHash, Hashing.sha256());
                        hashString = hashCode.toString();
                    }
                    tile.put("sha256", hashString);
                    tile.put("tile_format", "TIFF");
                    tile.set("tile_shape", plane);
                    tiles.add(tile);
                }
            }
        }
        primary.set("tiles", tiles);
        primary.put("version", "1.0.0");
        String name = String.format("%s/%s", out, naming.getJsonFilename(fov));
        writer.writeValue(new File(name), primary);
    }

    /**
     * Return the given position in micrometers or null if the value is not found
     * or it is not convertible to micrometers.
     */
    private Double getPosition(String idx, int z, int c, int t) {
        Length length;

        // Validate indexes
        int imageIndex = reader.getSeries();
        int planeIndex = FormatTools.getIndex(reader, z, c, t);

        // Perform lookup
        switch (idx) {
            case "xc":
                length = meta.getPlanePositionX(imageIndex, planeIndex);
                break;
            case "yc":
                length = meta.getPlanePositionY(imageIndex, planeIndex);
                break;
            case "zc":
                length = meta.getPlanePositionZ(imageIndex, planeIndex);
                break;
            default:
                throw new RuntimeException("unknown: " + idx);
        }

        // Convert if possible
        if (length == null || !length.unit().isConvertible(UNITS.MICROMETER)) {
            return null;
        }
        return length.value(UNITS.MICROMETER).doubleValue();
    }


}
