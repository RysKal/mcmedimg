package suprk.com;

// Info about one slice

import java.nio.file.Path;

public record DicomSlice(
        Path path,
        int rows,
        int columns,
        double slope,
        double intercept,
        double position
) {}