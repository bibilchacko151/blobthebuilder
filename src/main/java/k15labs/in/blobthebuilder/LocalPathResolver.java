package k15labs.in.blobthebuilder;

import java.io.IOException;
import java.nio.file.Files;
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

    public void ensureDirectoryTree(Path baseOutputDirectory, Path targetDirectory) throws IOException {
        if (baseOutputDirectory == null) {
            throw new IllegalArgumentException("Invalid base output directory: null");
        }
        if (targetDirectory == null) {
            throw new IllegalArgumentException("Invalid target directory: null");
        }

        Path base = baseOutputDirectory.toAbsolutePath().normalize();
        Path target = targetDirectory.toAbsolutePath().normalize();
        if (!target.startsWith(base)) {
            throw new SecurityException("Directory resolves outside the output directory: " + target);
        }

        Path relative = base.relativize(target);
        Path current = base;
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.exists(current)) {
                if (!Files.isDirectory(current)) {
                    throw new IOException("File/directory collision: " + current + " exists as a file but is required as a directory.");
                }
            } else {
                Files.createDirectory(current);
            }
        }
    }
}
