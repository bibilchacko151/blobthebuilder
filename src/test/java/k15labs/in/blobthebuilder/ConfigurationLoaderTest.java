package k15labs.in.blobthebuilder;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigurationLoaderTest {

    private final ConfigurationLoader loader = new ConfigurationLoader();

    @Test
    void devConfigurationLoadsCorrectly() throws IOException {
        Path config = writeProperties("""
            storage-account=dev-account
            container=dev-container
            """);

        AzureStorageConfig loaded = loader.load(config, "dev");

        assertEquals("dev-account", loaded.storageAccount());
        assertEquals("dev-container", loaded.container());
    }

    @Test
    void prdConfigurationLoadsCorrectly() throws IOException {
        Path config = writeProperties("""
            storage-account=prd-account
            container=prd-container
            """);

        AzureStorageConfig loaded = loader.load(config, "prd");

        assertEquals("prd-account", loaded.storageAccount());
        assertEquals("prd-container", loaded.container());
    }

    @Test
    void missingStorageAccountPropertyIsRejected() throws IOException {
        Path config = writeProperties("""
            container=prd-container
            """);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> loader.load(config, "prd"));
        assertEquals("Missing configuration property: storage-account", exception.getMessage());
    }

    @Test
    void missingContainerPropertyIsRejected() throws IOException {
        Path config = writeProperties("""
            storage-account=prd-account
            """);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> loader.load(config, "prd"));
        assertEquals("Missing configuration property: container", exception.getMessage());
    }

    @Test
    void propertiesFileNotFoundIsHandled() {
        Path config = Path.of("does-not-exist.properties");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> loader.load(config, "dev"));
        assertEquals("Properties file not found: " + config.toAbsolutePath().normalize(), exception.getMessage());
    }

    @Test
    void customPropertiesFilePathWorks() throws IOException {
        Path config = writeProperties("""
            storage-account=custom-account
            container=custom-container
            """);

        AzureStorageConfig loaded = loader.load(config, "dev");

        assertEquals("custom-account", loaded.storageAccount());
        assertEquals("custom-container", loaded.container());
    }

    private Path writeProperties(String content) throws IOException {
        Path file = Files.createTempFile("pnr-downloader", ".properties");
        Files.writeString(file, content);
        return file;
    }
}

