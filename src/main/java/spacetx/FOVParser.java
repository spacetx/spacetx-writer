package spacetx;

import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.ImageReader;
import loci.formats.MetadataTools;
import loci.formats.in.DynamicMetadataOptions;
import loci.formats.meta.MetadataStore;
import loci.formats.ome.OMEXMLMetadata;
import loci.formats.services.OMEXMLService;

import java.io.IOException;



public class FOVParser {

    private String input;

    private int plateCount;

    private int seriesCount;

    private OMEXMLMetadata meta;

    private ImageReader reader;

    public FOVParser(String input) throws IOException, FormatException {
        this.input = input;

        // First use the generic reader object to load the metadata
        DynamicMetadataOptions options = new DynamicMetadataOptions();
        options.setValidate(true);
        meta = null;
        try {
            OMEXMLService xml = new ServiceFactory().getInstance(OMEXMLService.class);
            meta = xml.createOMEXMLMetadata();
        } catch (ServiceException | DependencyException exc) {
            throw new FormatException("Error creating metadata service");
        }
        reader = new ImageReader();
        reader.setMetadataOptions(options);
        reader.setGroupFiles(true);
        reader.setMetadataFiltered(true);
        reader.setOriginalMetadataPopulated(true);
        reader.setMetadataStore(meta);
        reader.setId(input);
        MetadataStore store = reader.getMetadataStore();
        // doPlane: true is critical for position information
        MetadataTools.populatePixels(store, reader, true, false);

        plateCount = meta.getPlateCount();
        seriesCount = reader.getSeriesCount();

    }

    public String getInput() {
        return input;
    }

    public int getPlateCount() {
        return plateCount;
    }

    public int getSeriesCount() {
        return seriesCount;
    }

    public OMEXMLMetadata getMetadata() {
        return meta;
    }

    public ImageReader getReader() {
        return reader;
    }

    public void close() throws IOException {
        reader.close();
    }
}
