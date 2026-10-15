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
import java.util.*;
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

    public final int sizeX; // layers
    public final int sizeY; // rows
    public final int sizeZ; // columns

    private final boolean dicom;

    private final NiftiVolume niftiImg;

    private final List<DicomSlice> dicomSlices;

    // cache for one DICOM slice
    private int cachedLayer = -1;
    private Raster cachedRaster = null;

    // DICOM series

    private final List<DicomSeriesInfo> dicomSeriesInfos;
    private final int selectedDicomSeriesIndex;

    // NIFTI constructor

    public MedicalImage(NiftiVolume img) {

        this.niftiImg = img;
        this.dicomSlices = null;
        this.dicom = false;

        this.dicomSeriesInfos = List.of();
        this.selectedDicomSeriesIndex = -1;

        this.sizeX = img.data.sizeX();
        this.sizeY = img.data.sizeY();
        this.sizeZ = img.data.sizeZ();
    }

    // DICOM constructor

    public MedicalImage(
            Path dicomDirectory,
            Integer requestedSeriesNumber
    ) throws IOException {

        this.niftiImg = null;
        this.dicom = true;


        List<DicomSeriesGroup> groups =
                loadDicomSeriesGroups(
                        dicomDirectory
                );


        if (groups.isEmpty()) {
            throw new IOException(
                    "No DICOM image series found"
            );
        }

        int selectedIndex;

        // Longest by default
        if (requestedSeriesNumber == null) {

            selectedIndex = 0;

            int largestSize =
                    groups.getFirst()
                            .slices
                            .size();


            for (int i = 1;
                 i < groups.size();
                 i++) {

                int currentSize =
                        groups.get(i)
                                .slices
                                .size();

                if (currentSize > largestSize) {

                    largestSize =
                            currentSize;

                    selectedIndex =
                            i;
                }
            }

        } else {

            // User starts from 1
            if (requestedSeriesNumber < 1
                    || requestedSeriesNumber > groups.size()) {

                throw new IOException(
                        "Series out of bounds. "
                                + "Available series: 1-"
                                + groups.size()
                );
            }

            selectedIndex =
                    requestedSeriesNumber - 1;
        }


        this.selectedDicomSeriesIndex =
                selectedIndex;


        DicomSeriesGroup selected =
                groups.get(
                        selectedIndex
                );


        this.dicomSlices =
                List.copyOf(
                        selected.slices
                );

        // List of series
        List<DicomSeriesInfo> infos =
                new ArrayList<>();

        for (int i = 0;
             i < groups.size();
             i++) {

            DicomSeriesGroup group =
                    groups.get(i);


            infos.add(
                    new DicomSeriesInfo(
                            i + 1,

                            group.uid,
                            group.description,

                            group.seriesNumber,
                            group.acquisitionNumber,

                            group.modality,

                            group.repetitionTime,
                            group.echoTime,
                            group.inversionTime,

                            group.imageType,

                            group.slices.size(),
                            group.rows,
                            group.columns,

                            i == selectedIndex
                    )
            );
        }

        this.dicomSeriesInfos =
                List.copyOf(
                        infos
                );

        this.sizeX =
                dicomSlices.size();

        this.sizeY =
                selected.rows;

        this.sizeZ =
                selected.columns;

        MedicalImages.LOGGER.info(
                "Found {} DICOM series. "
                        + "Selected series {} with {} slices.",
                groups.size(),
                selectedIndex + 1,
                dicomSlices.size()
        );
    }

    public MedicalImage(
            Path dicomDirectory
    ) throws IOException {

        this(
                dicomDirectory,
                null
        );
    }

    private static class DicomSeriesGroup {

        final String uid;
        final String description;

        final int seriesNumber;
        final int acquisitionNumber;

        final String modality;

        final double repetitionTime;
        final double echoTime;
        final double inversionTime;

        final String imageType;

        final int rows;
        final int columns;

        final List<DicomSlice> slices =
                new ArrayList<>();

        DicomSeriesGroup(
                String uid,
                String description,
                int seriesNumber,
                int acquisitionNumber,
                String modality,
                double repetitionTime,
                double echoTime,
                double inversionTime,
                String imageType,
                int rows,
                int columns
        ) {

            this.uid = uid;
            this.description = description;

            this.seriesNumber = seriesNumber;
            this.acquisitionNumber = acquisitionNumber;

            this.modality = modality;

            this.repetitionTime = repetitionTime;
            this.echoTime = echoTime;
            this.inversionTime = inversionTime;

            this.imageType = imageType;

            this.rows = rows;
            this.columns = columns;
        }
    }

    private static List<DicomSeriesGroup> loadDicomSeriesGroups(
            Path directory
    ) throws IOException {

        Map<String, DicomSeriesGroup> groups =
                new LinkedHashMap<>();

        try (Stream<Path> files =
                     Files.list(directory)) {

            for (Path file :
                    files.filter(Files::isRegularFile).toList()) {

                try (DicomInputStream dis =
                             new DicomInputStream(
                                     file.toFile()
                             )) {

                    dis.setIncludeBulkData(
                            DicomInputStream.IncludeBulkData.NO
                    );

                    Attributes attributes =
                            dis.readDatasetUntilPixelData();


                    int rows =
                            attributes.getInt(
                                    Tag.Rows,
                                    0
                            );

                    int columns =
                            attributes.getInt(
                                    Tag.Columns,
                                    0
                            );


                    // Ommit empty or faulty
                    if (rows <= 0 || columns <= 0) {
                        continue;
                    }


                    String uid =
                            attributes.getString(
                                    Tag.SeriesInstanceUID
                            );

                    String description =
                            attributes.getString(
                                    Tag.SeriesDescription
                            );

                    String modality =
                            attributes.getString(
                                    Tag.Modality
                            );

                    int seriesNumber =
                            attributes.getInt(
                                    Tag.SeriesNumber,
                                    -1
                            );

                    int acquisitionNumber =
                            attributes.getInt(
                                    Tag.AcquisitionNumber,
                                    -1
                            );

                    double repetitionTime =
                            attributes.getDouble(
                                    Tag.RepetitionTime,
                                    Double.NaN
                            );

                    double echoTime =
                            attributes.getDouble(
                                    Tag.EchoTime,
                                    Double.NaN
                            );

                    double inversionTime =
                            attributes.getDouble(
                                    Tag.InversionTime,
                                    Double.NaN
                            );

                    String[] imageTypes =
                            attributes.getStrings(
                                    Tag.ImageType
                            );

                    String imageType =
                            imageTypes == null
                                    ? "?"
                                    : String.join("\\", imageTypes);

                    if (uid == null || uid.isBlank()) {
                        uid = "UNKNOWN";
                    }

                    if (description == null
                            || description.isBlank()) {

                        description =
                                "(no description)";
                    }

                    if (modality == null
                            || modality.isBlank()) {

                        modality = "?";
                    }


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
                            getSlicePosition(
                                    attributes
                            );


                    DicomSlice slice =
                            new DicomSlice(
                                    file,
                                    rows,
                                    columns,
                                    slope,
                                    intercept,
                                    position
                            );

                    // by UID & size
                    String key =
                            uid
                                    + "|"
                                    + rows
                                    + "x"
                                    + columns;


                    DicomSeriesGroup group =
                            groups.get(key);

                    if (group == null) {

                        group =
                                new DicomSeriesGroup(
                                        uid,
                                        description,
                                        seriesNumber,
                                        acquisitionNumber,
                                        modality,
                                        repetitionTime,
                                        echoTime,
                                        inversionTime,
                                        imageType,
                                        rows,
                                        columns
                                );

                        groups.put(
                                key,
                                group
                        );
                    }


                    group.slices.add(
                            slice
                    );


                } catch (Exception e) {

                    MedicalImages.LOGGER.warn(
                            "Skipping DICOM file {}: {}",
                            file,
                            e.getMessage()
                    );
                }
            }
        }


        List<DicomSeriesGroup> result =
                new ArrayList<>(
                        groups.values()
                );


        // Sort list of series
        result.sort(
                Comparator
                        .comparingInt(
                                (DicomSeriesGroup group) ->
                                        group.seriesNumber < 0
                                                ? Integer.MAX_VALUE
                                                : group.seriesNumber
                        )
                        .thenComparing(
                                group ->
                                        group.description
                        )
                        .thenComparing(
                                group ->
                                        group.uid
                        )
        );


        // Sort each series
        for (DicomSeriesGroup group : result) {

            group.slices.sort(
                    Comparator.comparingDouble(
                            DicomSlice::position
                    )
            );
        }


        return result;
    }

    public boolean isDicom() {
        return dicom;
    }

    public List<DicomSeriesInfo> getDicomSeriesInfos() {
        return dicomSeriesInfos;
    }

    public DicomSeriesInfo getSelectedDicomSeriesInfo() {

        if (!dicom
                || selectedDicomSeriesIndex < 0) {

            return null;
        }

        return dicomSeriesInfos.get(
                selectedDicomSeriesIndex
        );
    }

    // getter of value for both types

    public double get(int layer, int y, int z) throws IOException {

        if (!dicom) {
            return niftiImg.data.get(layer, y, z, 0);
        }

        DicomSlice slice = dicomSlices.get(layer);

        Raster raster = getDicomRaster(layer);

        // x = column
        // y = row
        double raw = raster.getSampleDouble(z, y, 0);

        return raw * slice.slope() + slice.intercept();
    }
}