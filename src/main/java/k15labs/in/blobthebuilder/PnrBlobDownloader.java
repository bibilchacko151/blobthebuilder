package k15labs.in.blobthebuilder;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.ListBlobsOptions;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PnrBlobDownloader {
    private static final String ROOT_PREFIX = "invision_sbr_audit";
    private static final Logger LOGGER = Logger.getLogger(PnrBlobDownloader.class.getName());

    private final LocalPathResolver localPathResolver;

    public PnrBlobDownloader() {
        this(new LocalPathResolver());
    }

    PnrBlobDownloader(LocalPathResolver localPathResolver) {
        this.localPathResolver = localPathResolver;
    }

    public int downloadPnr(AzureStorageConfig config, String environment, String pnr, Path outputDirectory) {
        validateOutputDirectory(outputDirectory);

        Path baseOutputDirectory = outputDirectory.toAbsolutePath().normalize();
        try {
            localPathResolver.ensureDirectoryTree(baseOutputDirectory, baseOutputDirectory);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid local output directory: " + baseOutputDirectory, e);
        }
        String prefix = ROOT_PREFIX + "/" + pnr + "/";

        LOGGER.info(() -> "Environment       : " + environment);
        LOGGER.info(() -> "Storage Account   : " + config.storageAccount());
        LOGGER.info(() -> "Container         : " + config.container());
        LOGGER.info(() -> "PNR               : " + pnr);
        LOGGER.info(() -> "Blob Prefix       : " + prefix);
        LOGGER.info(() -> "Output Directory  : " + baseOutputDirectory);

        BlobServiceClient blobServiceClient = buildBlobServiceClient(config.storageAccount());
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(config.container());

        int successCount = 0;
        int failureCount = 0;
        boolean foundAny = false;

        for (BlobItem blobItem : containerClient.listBlobs(new ListBlobsOptions().setPrefix(prefix), null)) {
            foundAny = true;
            String blobName = blobItem.getName();
            if (blobName == null || blobName.isBlank() || blobName.endsWith("/")) {
                continue;
            }
            Path destination = null;
            try {
                destination = localPathResolver.buildLocalPath(baseOutputDirectory, blobName);
                downloadBlob(containerClient.getBlobClient(blobName), baseOutputDirectory, destination, blobName);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                logFailure(blobName, destination != null ? destination : baseOutputDirectory, e);
            }
        }

        if (!foundAny) {
            LOGGER.info(() -> "No blobs found for PNR: " + pnr);
            return 0;
        }

        Path completedOutput = baseOutputDirectory.resolve(ROOT_PREFIX).resolve(pnr).normalize();
        LOGGER.info("");
        LOGGER.info("Download completed");
        LOGGER.info("");
        LOGGER.info(() -> "Environment                  : " + environment);
        LOGGER.info(() -> "PNR                          : " + pnr);
        LOGGER.info("Files downloaded successfully: " + successCount);
        LOGGER.info("Files failed                 : " + failureCount);
        LOGGER.info(() -> "Output directory             : " + completedOutput);

        return failureCount > 0 ? 1 : 0;
    }

    BlobServiceClient buildBlobServiceClient(String storageAccount) {
        DefaultAzureCredential credential = new DefaultAzureCredentialBuilder().build();
        return new BlobServiceClientBuilder()
            .endpoint("https://" + storageAccount + ".blob.core.windows.net")
            .credential(credential)
            .buildClient();
    }

    void downloadBlob(BlobClient blobClient, Path baseOutputDirectory, Path destination, String blobName) throws IOException {
        LOGGER.info("Downloading:");
        LOGGER.info(blobName);
        LOGGER.info(() -> "-> " + destination);
        Path parent = destination.getParent();
        if (parent != null) {
            localPathResolver.ensureDirectoryTree(baseOutputDirectory, parent);
        }
        try (OutputStream outputStream = Files.newOutputStream(
            destination,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )) {
            blobClient.downloadStream(outputStream);
        }
    }

    private void logFailure(String blobName, Path destination, Exception exception) {
        LOGGER.severe("Failed to download blob : " + blobName);
        LOGGER.severe("Destination             : " + destination);
        LOGGER.severe("Exception                : " + exception.getClass().getName());
        LOGGER.severe("Reason                   : " + exception.getMessage());
        if (exception instanceof IOException && exception.getMessage() != null && exception.getMessage().startsWith("File/directory collision:")) {
            LOGGER.severe("Collision                : " + exception.getMessage());
        }
    }

    private void validateOutputDirectory(Path outputDirectory) {
        if (outputDirectory == null) {
            throw new IllegalArgumentException("Invalid output directory: null");
        }
    }
}
