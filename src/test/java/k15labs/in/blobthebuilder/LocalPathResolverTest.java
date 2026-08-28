package k15labs.in.blobthebuilder;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalPathResolverTest {

    private final LocalPathResolver resolver = new LocalPathResolver();

    @Test
    void buildsNormalNestedBlobPath() {
        Path output = Path.of("/tmp/downloads");
        Path destination = resolver.buildLocalPath(output, "invision_sbr_audit/ABC123/0/1756383920000/request.json");

        assertEquals(output.toAbsolutePath().normalize()
            .resolve("invision_sbr_audit")
            .resolve("ABC123")
            .resolve("0")
            .resolve("1756383920000")
            .resolve("request.json")
            .normalize(), destination);
    }

    @Test
    void preservesNestedTimestampFolders() {
        Path output = Path.of("downloads");
        Path destination = resolver.buildLocalPath(output, "invision_sbr_audit/ABC123/1/1756383920000/details/raw/request.json");

        assertTrue(destination.toString().contains("details"));
        assertTrue(destination.toString().contains("raw"));
        assertEquals(output.toAbsolutePath().normalize()
            .resolve("invision_sbr_audit")
            .resolve("ABC123")
            .resolve("1")
            .resolve("1756383920000")
            .resolve("details")
            .resolve("raw")
            .resolve("request.json")
            .normalize(), destination);
    }

    @Test
    void rejectsDotSegment() {
        SecurityException exception = assertThrows(SecurityException.class, () ->
            resolver.buildLocalPath(Path.of("downloads"), "invision_sbr_audit/ABC123/./request.json")
        );
        assertEquals("Rejected suspicious path segment: .", exception.getMessage());
    }

    @Test
    void rejectsDotDotSegment() {
        SecurityException exception = assertThrows(SecurityException.class, () ->
            resolver.buildLocalPath(Path.of("downloads"), "invision_sbr_audit/ABC123/../request.json")
        );
        assertEquals("Rejected suspicious path segment: ..", exception.getMessage());
    }

    @Test
    void rejectsPathTraversalOutsideOutputDirectory() {
        SecurityException exception = assertThrows(SecurityException.class, () ->
            resolver.buildLocalPath(Path.of("downloads"), "invision_sbr_audit/ABC123/../../evil.json")
        );
        assertTrue(exception.getMessage().contains("Rejected suspicious path segment") || exception.getMessage().contains("outside the output directory"));
    }

    @Test
    void handlesWindowsCompatibleOutputPathInput() {
        Path output = Path.of("C:\\Downloads");
        Path destination = resolver.buildLocalPath(output, "invision_sbr_audit/ABC123/0/1756383920000/response.json");

        assertTrue(destination.toString().contains("invision_sbr_audit"));
        assertTrue(destination.toString().contains("ABC123"));
        assertTrue(destination.toString().contains("response.json"));
    }

    @Test
    void createsFullDirectoryTreeWhenNestedDirectoriesDoNotExist() throws IOException {
        Path base = Files.createTempDirectory("blob-downloader-base");
        Path target = base
            .resolve("invisio-sbr-audit")
            .resolve("2LSNQF")
            .resolve("99")
            .resolve("2026-04-27_20260427012921");

        resolver.ensureDirectoryTree(base, target);

        assertTrue(Files.isDirectory(target));
        assertTrue(Files.isDirectory(target.getParent()));
    }

    @Test
    void succeedsWhenAllRequiredDirectoriesAlreadyExist() throws IOException {
        Path base = Files.createTempDirectory("blob-downloader-base");
        Path target = base
            .resolve("invisio-sbr-audit")
            .resolve("2LSNQF")
            .resolve("99")
            .resolve("2026-04-27_20260427012921");
        Files.createDirectories(target);

        assertDoesNotThrow(() -> resolver.ensureDirectoryTree(base, target));
        assertTrue(Files.isDirectory(target));
    }

    @Test
    void rejectsIntermediateFileCollision() throws IOException {
        Path base = Files.createTempDirectory("blob-downloader-base");
        Path collidingFile = base
            .resolve("invisio-sbr-audit")
            .resolve("2LSNQF")
            .resolve("99");
        Files.createDirectories(collidingFile.getParent());
        Files.createFile(collidingFile);

        Path target = base
            .resolve("invisio-sbr-audit")
            .resolve("2LSNQF")
            .resolve("99")
            .resolve("2026-04-27_20260427012921");

        IOException exception = assertThrows(IOException.class, () -> resolver.ensureDirectoryTree(base, target));
        assertTrue(exception.getMessage().contains("File/directory collision"));
        assertTrue(exception.getMessage().contains(collidingFile.toString()));
    }

    @Test
    void createsNestedDirectoriesAcrossMultipleLevels() throws IOException {
        Path base = Files.createTempDirectory("blob-downloader-base");
        Path target = base
            .resolve("alpha")
            .resolve("bravo")
            .resolve("charlie")
            .resolve("delta")
            .resolve("echo");

        resolver.ensureDirectoryTree(base, target);

        assertTrue(Files.isDirectory(target));
        assertTrue(Files.isDirectory(target.getParent()));
        assertTrue(Files.isDirectory(target.getParent().getParent()));
    }

    @Test
    void rejectsPathTraversalForDirectoryCreation() throws IOException {
        Path base = Files.createTempDirectory("blob-downloader-base");
        Path outside = base.getParent().resolve("outside");

        SecurityException exception = assertThrows(SecurityException.class, () ->
            resolver.ensureDirectoryTree(base, outside)
        );
        assertTrue(exception.getMessage().contains("outside the output directory"));
    }

    @Test
    void usesPortablePathResolutionWithoutHardCodedSeparators() {
        Path output = Path.of("downloads");
        Path destination = resolver.buildLocalPath(output, Path.of("invisio-sbr-audit", "2LSNQF", "99", "metadata.json").toString());

        assertFalse(destination.toString().contains("//"));
        assertTrue(destination.toString().contains("invisio-sbr-audit"));
        assertTrue(destination.toString().contains("metadata.json"));
    }

    @Test
    void replacesStaleZeroByteMarkerFileWithDirectory() throws IOException {
        Path base = Files.createTempDirectory("blob-downloader-base");
        Path marker = base.resolve("root").resolve("PNR").resolve("99");
        Files.createDirectories(marker.getParent());
        Files.createFile(marker);

        boolean replaced = resolver.ensureMarkerDirectory(base, marker, true);

        assertTrue(replaced);
        assertTrue(Files.isDirectory(marker));
    }

    @Test
    void protectsNonZeroFileWhenDirectoryIsRequired() throws IOException {
        Path base = Files.createTempDirectory("blob-downloader-base");
        Path file = base.resolve("root").resolve("PNR").resolve("99");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "genuine local data");

        IOException exception = assertThrows(IOException.class, () -> resolver.ensureMarkerDirectory(base, file, true));

        assertTrue(exception.getMessage().contains("File/directory collision"));
        assertEquals("genuine local data", Files.readString(file));
    }

    @Test
    void resolvesTrailingSlashMarkerAsDirectory() {
        Path base = Path.of("downloads");

        Path marker = resolver.buildLocalDirectoryPath(base, "root/PNR/folder/");

        assertEquals(base.toAbsolutePath().normalize().resolve("root").resolve("PNR").resolve("folder"), marker);
    }

    @Test
    void rejectsPathTraversalInDirectoryMarker() {
        assertThrows(SecurityException.class, () ->
            resolver.buildLocalDirectoryPath(Path.of("downloads"), "root/PNR/../outside/")
        );
    }
}
