package k15labs.in.blobthebuilder;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}

