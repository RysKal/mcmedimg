package suprk.com;

// Load OpenCV to prevent errors on Windows with some DICOM datasets

import org.weasis.opencv.natives.NativeLibrary;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class OpenCvLoader {

    private static boolean loaded = false;

    private OpenCvLoader() {
    }

    public static synchronized void load() {

        if (loaded) {
            return;
        }

        String os =
                System.getProperty("os.name")
                        .toLowerCase();

        String arch =
                System.getProperty("os.arch")
                        .toLowerCase();


        if (!os.contains("win")) {
            throw new UnsupportedOperationException(
                    "OpenCV native loading is currently implemented only for Windows."
            );
        }

        if (!arch.contains("64")
                && !arch.contains("amd64")
                && !arch.contains("x86_64")) {

            throw new UnsupportedOperationException(
                    "Unsupported Windows architecture: "
                            + arch
            );
        }


        String resource =
                "/natives/windows-x86-64/opencv_java.dll";


        try (InputStream input =
                     OpenCvLoader.class
                             .getResourceAsStream(resource)) {

            if (input == null) {
                throw new RuntimeException(
                        "Bundled OpenCV native library not found: "
                                + resource
                );
            }


            Path directory =
                    Files.createTempDirectory(
                            "medicalimages-opencv-"
                    );


            Path dll =
                    directory.resolve(
                            "opencv_java.dll"
                    );


            Files.copy(
                    input,
                    dll,
                    StandardCopyOption.REPLACE_EXISTING
            );


            dll.toFile()
                    .deleteOnExit();

            directory.toFile()
                    .deleteOnExit();


            NativeLibrary.loadLibraryFromAbsolutePath(
                    dll.toAbsolutePath()
            );


            loaded = true;


            MedicalImages.LOGGER.info(
                    "Loaded bundled OpenCV native library from {}",
                    dll
            );


        } catch (IOException e) {

            throw new RuntimeException(
                    "Failed to extract OpenCV native library",
                    e
            );
        }
    }
}