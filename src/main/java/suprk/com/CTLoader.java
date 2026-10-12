package suprk.com;

import net.fabricmc.loader.api.FabricLoader;
import niftijio.NiftiVolume;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class CTLoader {

    public static MedicalImage load(String filename) throws IOException {

        Path directory = FabricLoader.getInstance()
                .getGameDir()
                .resolve("medicalimages")
                .toAbsolutePath()
                .normalize();

        Files.createDirectories(directory);

        Path path = directory
                .resolve(filename)
                .toAbsolutePath()
                .normalize();

        // zabezpieczenie przed ../../
        if (!path.startsWith(directory)) {
            throw new IOException("Invalid path: " + filename);
        }

        if (!Files.exists(path)) {
            throw new IOException("File or directory not found: " + path);
        }

        // DICOM series = katalog
        if (Files.isDirectory(path)) {
            return new MedicalImage(path);
        }

        // NIfTI = plik
        String name = path.getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);

        if (name.endsWith(".nii") || name.endsWith(".nii.gz")) {
            NiftiVolume nifti = NiftiVolume.read(path.toString());

            return new MedicalImage(nifti);
        }

        throw new IOException(
                "Unsupported medical image format: " + filename
        );
    }
}