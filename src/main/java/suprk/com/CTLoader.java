package suprk.com;

import net.fabricmc.loader.api.FabricLoader;
import niftijio.NiftiVolume;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class CTLoader {

    private static Path resolvePath(
            String filename
    ) throws IOException {

        Path directory =
                FabricLoader.getInstance()
                        .getGameDir()
                        .resolve("medicalimages")
                        .toAbsolutePath()
                        .normalize();

        Files.createDirectories(
                directory
        );


        Path path =
                directory
                        .resolve(filename)
                        .toAbsolutePath()
                        .normalize();


        if (!path.startsWith(directory)) {
            throw new IOException(
                    "Invalid path: "
                            + filename
            );
        }


        if (!Files.exists(path)) {
            throw new IOException(
                    "File or directory not found: "
                            + path
            );
        }


        return path;
    }


    public static MedicalImage load(
            String filename
    ) throws IOException {

        Path path =
                resolvePath(
                        filename
                );


        // DICOM uses directory
        if (Files.isDirectory(path)) {

            return new MedicalImage(
                    path
            );
        }


        String name =
                path.getFileName()
                        .toString()
                        .toLowerCase(
                                Locale.ROOT
                        );


        if (name.endsWith(".nii")
                || name.endsWith(".nii.gz")) {

            NiftiVolume nifti =
                    NiftiVolume.read(
                            path.toString()
                    );

            return new MedicalImage(
                    nifti
            );
        }


        throw new IOException(
                "Unsupported medical image format: "
                        + filename
        );
    }

    // Load one series
    public static MedicalImage load(
            String filename,
            int seriesNumber
    ) throws IOException {

        Path path =
                resolvePath(
                        filename
                );


        if (!Files.isDirectory(path)) {

            throw new IOException(
                    "Series selection is only available "
                            + "for DICOM directories."
            );
        }


        return new MedicalImage(
                path,
                seriesNumber
        );
    }
}