package k15labs.in.blobthebuilder;

import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        try {
            CommandLineArguments commandLineArguments = CommandLineArguments.parse(args);
            ConfigurationLoader configurationLoader = new ConfigurationLoader();
            AzureStorageConfig config = configurationLoader.load(commandLineArguments.configPath(), commandLineArguments.environment());
            Path outputDirectory = commandLineArguments.outputDirectory();

            PnrBlobDownloader downloader = new PnrBlobDownloader();
            int exitCode = downloader.downloadPnr(
                config,
                commandLineArguments.environment(),
                commandLineArguments.pnr(),
                outputDirectory
            );
            System.exit(exitCode);
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}

