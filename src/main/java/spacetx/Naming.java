package spacetx;

/**
 * Enumeration of strategies for how files should be named on disk.
 */
public enum Naming {

    standard("primary_image-fov");

    private final String root;

    Naming(String root) {
        this.root = root;
    }

    public String getTiffPattern(int fov) {
        return String.format("%s_%03d_Z%%z_T%%t_C%%c.ome.tiff", root, fov);
    }

    public String getTiffFilename(int fov, int z, int t, int c) {
        return String.format("%s_%03d_Z%d_T%d_C%d.ome.tiff", root, fov, z, t, c);
    }

    public String getJsonFilename(int fov) {
        return String.format("%s_%03d.json", root, fov);
    }

    public String getCompanionFilename(int fov) {
        return String.format("%s_%03d.companion.ome", root, fov);
    }

}
