package k15labs.in.blobthebuilder;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.nio.file.Path;

public final class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

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
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            System.exit(1);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error: " + e.getMessage(), e);
            System.exit(1);
        }
    }
}
