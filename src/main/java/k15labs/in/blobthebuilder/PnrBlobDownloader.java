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

public final class PnrBlobDownloader {
    private static final String ROOT_PREFIX = "invision_sbr_audit";

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
            Files.createDirectories(baseOutputDirectory);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid local output directory: " + baseOutputDirectory, e);
        }
        String prefix = ROOT_PREFIX + "/" + pnr + "/";

        System.out.println("Environment       : " + environment);
        System.out.println("Storage Account   : " + config.storageAccount());
        System.out.println("Container         : " + config.container());
        System.out.println("PNR               : " + pnr);
        System.out.println("Blob Prefix       : " + prefix);
        System.out.println("Output Directory  : " + baseOutputDirectory);

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
            try {
                Path destination = localPathResolver.buildLocalPath(baseOutputDirectory, blobName);
                downloadBlob(containerClient.getBlobClient(blobName), destination, blobName);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                System.err.println("Failed to download blob: " + blobName);
                System.err.println("Reason: " + e.getMessage());
            }
        }

        if (!foundAny) {
            System.out.println("No blobs found for PNR: " + pnr);
            return 0;
        }

        Path completedOutput = baseOutputDirectory.resolve(ROOT_PREFIX).resolve(pnr).normalize();
        System.out.println();
        System.out.println("Download completed");
        System.out.println();
        System.out.println("Environment                  : " + environment);
        System.out.println("PNR                          : " + pnr);
        System.out.println("Files downloaded successfully: " + successCount);
        System.out.println("Files failed                 : " + failureCount);
        System.out.println("Output directory             : " + completedOutput);

        return failureCount > 0 ? 1 : 0;
    }

    BlobServiceClient buildBlobServiceClient(String storageAccount) {
        DefaultAzureCredential credential = new DefaultAzureCredentialBuilder().build();
        return new BlobServiceClientBuilder()
            .endpoint("https://" + storageAccount + ".blob.core.windows.net")
            .credential(credential)
            .buildClient();
    }

    void downloadBlob(BlobClient blobClient, Path destination, String blobName) throws IOException {
        System.out.println("Downloading:");
        System.out.println(blobName);
        System.out.println("-> " + destination);
        Files.createDirectories(destination.getParent());
        try (OutputStream outputStream = Files.newOutputStream(
            destination,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )) {
            blobClient.downloadStream(outputStream);
        }
    }

    private void validateOutputDirectory(Path outputDirectory) {
        if (outputDirectory == null) {
            throw new IllegalArgumentException("Invalid output directory: null");
        }
    }
}

