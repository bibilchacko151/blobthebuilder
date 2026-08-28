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

    public Path buildLocalDirectoryPath(Path outputDirectory, String blobName) {
        if (blobName == null || blobName.isBlank()) {
            throw new IllegalArgumentException("Invalid blob path: " + blobName);
        }
        String directoryName = blobName;
        while (directoryName.endsWith("/")) {
            directoryName = directoryName.substring(0, directoryName.length() - 1);
        }
        if (directoryName.isBlank()) {
            throw new IllegalArgumentException("Invalid directory marker path: " + blobName);
        }
        return buildLocalPath(outputDirectory, directoryName);
    }

    public boolean ensureMarkerDirectory(Path baseOutputDirectory, Path targetDirectory, boolean replaceStaleMarkerFile)
        throws IOException {
        Path base = normalizedWithinBase(baseOutputDirectory, targetDirectory);
        Path target = targetDirectory.toAbsolutePath().normalize();
        Path relative = base.relativize(target);
        Path current = base;
        boolean replaced = false;

        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.exists(current)) {
                if (Files.isDirectory(current)) {
                    continue;
                }
                if (replaceStaleMarkerFile && current.equals(target) && Files.isRegularFile(current) && Files.size(current) == 0) {
                    Files.delete(current);
                    Files.createDirectory(current);
                    replaced = true;
                    continue;
                }
                throw collision(current);
            }
            Files.createDirectory(current);
        }
        return replaced;
    }

    public void ensureDirectoryTree(Path baseOutputDirectory, Path targetDirectory) throws IOException {
        if (baseOutputDirectory == null) {
            throw new IllegalArgumentException("Invalid base output directory: null");
        }
        if (targetDirectory == null) {
            throw new IllegalArgumentException("Invalid target directory: null");
        }

        Path base = normalizedWithinBase(baseOutputDirectory, targetDirectory);
        Path target = targetDirectory.toAbsolutePath().normalize();

        Path relative = base.relativize(target);
        Path current = base;
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.exists(current)) {
                if (!Files.isDirectory(current)) {
                    throw collision(current);
                }
            } else {
                Files.createDirectory(current);
            }
        }
    }

    private Path normalizedWithinBase(Path baseOutputDirectory, Path targetDirectory) {
        Path base = baseOutputDirectory.toAbsolutePath().normalize();
        Path target = targetDirectory.toAbsolutePath().normalize();
        if (!target.startsWith(base)) {
            throw new SecurityException("Directory resolves outside the output directory: " + target);
        }
        return base;
    }

    private IOException collision(Path path) {
        return new IOException("File/directory collision: " + path + " exists as a file but is required as a directory.");
    }
}
