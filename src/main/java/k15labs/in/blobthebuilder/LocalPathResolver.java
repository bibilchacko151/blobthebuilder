package k15labs.in.blobthebuilder;

import java.nio.file.Path;

public final class LocalPathResolver {

    public Path buildLocalPath(Path outputDirectory, String blobName) {
        if (blobName == null || blobName.isBlank()) {
            throw new IllegalArgumentException("Invalid blob path: " + blobName);
        }
        if (blobName.endsWith("/")) {
            throw new IllegalArgumentException("Directory marker blobs must be skipped: " + blobName);
        }

        Path baseOutputDirectory = outputDirectory.toAbsolutePath().normalize();
        Path destination = baseOutputDirectory;

        String[] segments = blobName.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty()) {
                throw new IllegalArgumentException("Invalid blob path segment in: " + blobName);
            }
            if (".".equals(segment) || "..".equals(segment)) {
                throw new SecurityException("Rejected suspicious path segment: " + segment);
            }
            destination = destination.resolve(segment);
        }

        Path normalizedDestination = destination.toAbsolutePath().normalize();
        if (!normalizedDestination.startsWith(baseOutputDirectory)) {
            throw new SecurityException("Blob path resolves outside the output directory: " + blobName);
        }
        return normalizedDestination;
    }
}

