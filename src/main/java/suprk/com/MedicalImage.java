package suprk.com;

import niftijio.NiftiVolume;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReader;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReaderSpi;
import org.dcm4che3.io.DicomInputStream;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.Raster;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class MedicalImage {

    private Raster getDicomRaster(int layer) throws IOException {

        if (cachedLayer == layer && cachedRaster != null) {
            return cachedRaster;
        }

        DicomSlice slice = dicomSlices.get(layer);

        DicomImageReaderSpi spi = new DicomImageReaderSpi();
        DicomImageReader reader =
                (DicomImageReader) spi.createReaderInstance();

        try (ImageInputStream input =
                     ImageIO.createImageInputStream(slice.path().toFile())) {

            reader.setInput(input);

            cachedRaster = reader.readRaster(0, null);
            cachedLayer = layer;

            return cachedRaster;

        } finally {
            reader.dispose();
        }
    }

    private static double getSlicePosition(Attributes attributes) {

        double[] orientation =
                attributes.getDoubles(
                        Tag.ImageOrientationPatient
                );

        double[] position =
                attributes.getDoubles(
                        Tag.ImagePositionPatient
                );

        if (orientation == null ||
                orientation.length != 6 ||
                position == null ||
                position.length != 3) {

            return attributes.getInt(
                    Tag.InstanceNumber,
                    0
            );
        }

        double rx = orientation[0];
        double ry = orientation[1];
        double rz = orientation[2];

        double cx = orientation[3];
        double cy = orientation[4];
        double cz = orientation[5];

        double nx = ry * cz - rz * cy;
        double ny = rz * cx - rx * cz;
        double nz = rx * cy - ry * cx;

        return position[0] * nx
                + position[1] * ny
                + position[2] * nz;
    }

    private static List<DicomSlice> loadDicomSeries(Path directory)
            throws IOException {

        List<DicomSlice> slices = new ArrayList<>();

        try (Stream<Path> files = Files.list(directory)) {

            for (Path file : files.filter(Files::isRegularFile).toList()) {

                try (DicomInputStream dis =
                             new DicomInputStream(file.toFile())) {

                    dis.setIncludeBulkData(
                            DicomInputStream.IncludeBulkData.NO
                    );

                    Attributes attributes =
                            dis.readDatasetUntilPixelData();

                    int rows =
                            attributes.getInt(Tag.Rows, 0);

                    int columns =
                            attributes.getInt(Tag.Columns, 0);

                    double slope =
                            attributes.getDouble(
                                    Tag.RescaleSlope,
                                    1.0
                            );

                    double intercept =
                            attributes.getDouble(
                                    Tag.RescaleIntercept,
                                    0.0
                            );

                    double position =
                            getSlicePosition(attributes);

                    slices.add(
                            new DicomSlice(
                                    file,
                                    rows,
                                    columns,
                                    slope,
                                    intercept,
                                    position
                            )
                    );
                }
            }
        }

        slices.sort(
                Comparator.comparingDouble(
                        DicomSlice::position
                )
        );

        return slices;
    }

    public final int sizeX; // layers
    public final int sizeY; // rows
    public final int sizeZ; // columns

    private final boolean dicom;

    private final NiftiVolume niftiImg;

    private final List<DicomSlice> dicomSlices;

    // cache jednej warstwy DICOM
    private int cachedLayer = -1;
    private Raster cachedRaster = null;


    // =========================
    // NIFTI
    // =========================

    public MedicalImage(NiftiVolume img) {

        this.niftiImg = img;
        this.dicomSlices = null;
        this.dicom = false;

        this.sizeX = img.data.sizeX();
        this.sizeY = img.data.sizeY();
        this.sizeZ = img.data.sizeZ();
    }


    // =========================
    // DICOM
    // =========================

    public MedicalImage(Path dicomDirectory) throws IOException {

        this.niftiImg = null;
        this.dicom = true;

        this.dicomSlices = loadDicomSeries(dicomDirectory);

        if (dicomSlices.isEmpty()) {
            throw new IOException("No DICOM slices found");
        }

        DicomSlice first = dicomSlices.getFirst();

        this.sizeX = dicomSlices.size();
        this.sizeY = first.rows();
        this.sizeZ = first.columns();
    }


    // =========================
    // UNIFIED ACCESS
    // =========================

    public double get(int layer, int y, int z) throws IOException {

        if (!dicom) {
            return niftiImg.data.get(layer, y, z, 0);
        }

        DicomSlice slice = dicomSlices.get(layer);

        Raster raster = getDicomRaster(layer);

        // Raster używa:
        // x = column
        // y = row
        double raw = raster.getSampleDouble(z, y, 0);

        return raw * slice.slope() + slice.intercept();
    }
}