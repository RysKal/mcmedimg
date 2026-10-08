package suprk.com;

import niftijio.NiftiVolume;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class CTLoader {

    public static NiftiVolume load() throws IOException {

        try (InputStream resource = CTLoader.class
                .getClassLoader()
                .getResourceAsStream("assets/medicalimages/imaging.nii.gz")) {

            if (resource == null) {
                throw new IOException("Could not find imaging.nii.gz");
            }

            Path tempFile = Files.createTempFile(
                    "medicalimages_imaging_",
                    ".nii.gz"
            );

            Files.copy(
                    resource,
                    tempFile,
                    StandardCopyOption.REPLACE_EXISTING
            );

            tempFile.toFile().deleteOnExit();

            return NiftiVolume.read(tempFile.toString());
        }
    }
}