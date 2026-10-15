package suprk.com;

// This record is used to store info about individual series of DICOM images

public record DicomSeriesInfo(
        int index,

        String uid,
        String description,

        int seriesNumber,
        int acquisitionNumber,

        String modality,

        double repetitionTime,
        double echoTime,
        double inversionTime,

        String imageType,

        int sliceCount,
        int rows,
        int columns,

        boolean selected
) {
}