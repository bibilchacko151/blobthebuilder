package k15labs.in.blobthebuilder;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobListDetails;
import com.azure.storage.blob.models.ListBlobsOptions;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class PnrBlobDownloader {
    private static final String ROOT_PREFIX = "invision_sbr_audit";
    private static final Logger LOGGER = Logger.getLogger(PnrBlobDownloader.class.getName());

    private final LocalPathResolver localPathResolver;
    private final BlobInventoryPlanner inventoryPlanner;

    public PnrBlobDownloader() {
        this(new LocalPathResolver(), new BlobInventoryPlanner());
    }

    PnrBlobDownloader(LocalPathResolver localPathResolver) {
        this(localPathResolver, new BlobInventoryPlanner());
    }

    PnrBlobDownloader(LocalPathResolver localPathResolver, BlobInventoryPlanner inventoryPlanner) {
        this.localPathResolver = localPathResolver;
        this.inventoryPlanner = inventoryPlanner;
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

        List<BlobItem> discoveredBlobs = discoverBlobs(containerClient, prefix);
        if (discoveredBlobs.isEmpty()) {
            LOGGER.info(() -> "No blobs found for PNR: " + pnr);
            return 0;
        }

        List<BlobInventoryPlanner.PlannedBlob> plannedBlobs;
        try {
            plannedBlobs = inventoryPlanner.plan(discoveredBlobs);
        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "Unable to classify the Azure blob listing", e);
            return 1;
        }

        DownloadCounts counts = processPlan(containerClient, baseOutputDirectory, plannedBlobs);

        Path completedOutput = baseOutputDirectory.resolve(ROOT_PREFIX).resolve(pnr).normalize();
        LOGGER.info("");
        LOGGER.info("Download completed");
        LOGGER.info("");
        LOGGER.info(() -> "Environment                  : " + environment);
        LOGGER.info(() -> "PNR                          : " + pnr);
        LOGGER.info("Files downloaded successfully : " + counts.successCount);
        LOGGER.info("Directory markers skipped     : " + counts.directoryMarkerCount);
        LOGGER.info("Namespace conflicts           : " + counts.namespaceConflictCount);
        LOGGER.info("Files failed                  : " + counts.failureCount);
        LOGGER.info(() -> "Output directory             : " + completedOutput);

        return counts.failureCount > 0 || counts.namespaceConflictCount > 0 ? 1 : 0;
    }

    private List<BlobItem> discoverBlobs(BlobContainerClient containerClient, String prefix) {
        ListBlobsOptions options = new ListBlobsOptions()
            .setPrefix(prefix)
            .setDetails(new BlobListDetails().setRetrieveMetadata(true));
        List<BlobItem> discovered = new ArrayList<>();
        for (BlobItem blobItem : containerClient.listBlobs(options, null)) {
            discovered.add(blobItem);
        }
        return discovered;
    }

    private DownloadCounts processPlan(
        BlobContainerClient containerClient,
        Path baseOutputDirectory,
        List<BlobInventoryPlanner.PlannedBlob> plannedBlobs
    ) {
        DownloadCounts counts = new DownloadCounts();

        for (BlobInventoryPlanner.PlannedBlob blob : plannedBlobs) {
            if (blob.kind() == BlobInventoryPlanner.Kind.DIRECTORY_MARKER) {
                counts.directoryMarkerCount++;
                prepareDirectoryMarker(baseOutputDirectory, blob, counts);
            }
        }

        for (BlobInventoryPlanner.PlannedBlob blob : plannedBlobs) {
            if (blob.kind() == BlobInventoryPlanner.Kind.NAMESPACE_CONFLICT) {
                counts.namespaceConflictCount++;
                logNamespaceConflict(blob.blobName());
                prepareConflictDirectory(baseOutputDirectory, blob, counts);
            } else if (blob.kind() == BlobInventoryPlanner.Kind.UNCLASSIFIABLE_PARENT) {
                counts.failureCount++;
                IOException exception = new IOException(
                    "Blob is also a parent path, but its content length is unavailable and it cannot be safely classified"
                );
                logFailure(blob.blobName(), safeDirectoryDestination(baseOutputDirectory, blob), exception);
            }
        }

        for (BlobInventoryPlanner.PlannedBlob blob : plannedBlobs) {
            if (blob.kind() != BlobInventoryPlanner.Kind.FILE) {
                continue;
            }
            Path destination = null;
            try {
                destination = localPathResolver.buildLocalPath(baseOutputDirectory, blob.blobName());
                downloadBlob(containerClient.getBlobClient(blob.blobName()), baseOutputDirectory, destination, blob.blobName());
                counts.successCount++;
            } catch (Exception e) {
                counts.failureCount++;
                logFailure(blob.blobName(), destination != null ? destination : baseOutputDirectory, e);
            }
        }
        return counts;
    }

    private void prepareDirectoryMarker(
        Path baseOutputDirectory,
        BlobInventoryPlanner.PlannedBlob blob,
        DownloadCounts counts
    ) {
        Path destination = baseOutputDirectory;
        LOGGER.info("Directory marker:");
        LOGGER.info(blob.blobName());
        try {
            destination = localPathResolver.buildLocalDirectoryPath(baseOutputDirectory, blob.logicalPath());
            LOGGER.info("Creating directory:");
            LOGGER.info(destination.toString());
            if (blob.hasChildren() && Files.isRegularFile(destination) && Files.size(destination) == 0) {
                LOGGER.info("Replacing stale local marker file with directory:");
                LOGGER.info(destination.toString());
            }
            localPathResolver.ensureMarkerDirectory(baseOutputDirectory, destination, blob.hasChildren());
        } catch (Exception e) {
            counts.failureCount++;
            logFailure(blob.blobName(), destination, e);
        }
    }

    private void prepareConflictDirectory(
        Path baseOutputDirectory,
        BlobInventoryPlanner.PlannedBlob blob,
        DownloadCounts counts
    ) {
        Path destination = baseOutputDirectory;
        try {
            destination = localPathResolver.buildLocalDirectoryPath(baseOutputDirectory, blob.logicalPath());
            localPathResolver.ensureMarkerDirectory(baseOutputDirectory, destination, false);
        } catch (Exception e) {
            counts.failureCount++;
            logFailure(blob.blobName(), destination, e);
        }
    }

    private Path safeDirectoryDestination(Path baseOutputDirectory, BlobInventoryPlanner.PlannedBlob blob) {
        try {
            return localPathResolver.buildLocalDirectoryPath(baseOutputDirectory, blob.logicalPath());
        } catch (RuntimeException e) {
            return baseOutputDirectory;
        }
    }

    private void logNamespaceConflict(String blobName) {
        LOGGER.severe("Azure namespace conflict:");
        LOGGER.severe(blobName);
        LOGGER.severe("Reason:");
        LOGGER.severe("Blob contains genuine file data but is also required as a parent directory for child blobs.");
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
        LOGGER.info("Destination:");
        LOGGER.info(destination.toString());
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

    private static final class DownloadCounts {
        private int successCount;
        private int directoryMarkerCount;
        private int namespaceConflictCount;
        private int failureCount;
    }
}
