package k15labs.in.blobthebuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ConfigurationLoader {

    public AzureStorageConfig load(Path configPath, String environment) {
        Properties properties = new Properties();
        if (!Files.exists(configPath)) {
            throw new IllegalArgumentException("Properties file not found: " + configPath.toAbsolutePath().normalize());
        }

        try (InputStream inputStream = Files.newInputStream(configPath)) {
            properties.load(inputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read properties file: " + configPath.toAbsolutePath().normalize(), e);
        }

        String storageAccount = properties.getProperty("storage-account");
        if (storageAccount == null || storageAccount.isBlank()) {
            throw new IllegalArgumentException("Missing configuration property: storage-account");
        }

        String container = properties.getProperty("container");
        if (container == null || container.isBlank()) {
            throw new IllegalArgumentException("Missing configuration property: container");
        }

        return new AzureStorageConfig(storageAccount.trim(), container.trim());
    }
}
