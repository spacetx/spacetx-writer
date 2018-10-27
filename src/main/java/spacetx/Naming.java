package spacetx;

import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Enumeration of strategies for how files should be named on disk.
 */
public enum Naming {

    standard("primary_image-fov");

    private final String root;

    Naming(String root) {
        this.root = root;
    }

    public String getFOV(int fov) {
        return String.format("fov_%03d", fov);
    }

    public String getTiffPattern(int fov) {
        return String.format("%s_%03d_Z%%z_T%%t_C%%c.ome.tiff", root, fov);
    }

    public String getTiffFilename(int fov, int z, int t, int c) {
        return String.format("%s_%03d_Z%d_T%d_C%d.ome.tiff", root, fov, z, t, c);
    }

    public String getManifestFilename() {
        return String.format("%s.json", root);
    }

    public String getJsonFilename(int fov) {
        return String.format("%s_%03d.json", root, fov);
    }

    public String getCompanionFilename(int fov) {
        return String.format("%s_%03d.companion.ome", root, fov);
    }

    public PrettyPrinter createPrinter() {
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
        return printer;
    }
}
