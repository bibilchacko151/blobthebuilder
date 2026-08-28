package k15labs.in.blobthebuilder;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandLineArgumentsTest {

    @Test
    void parsesRequiredArguments() {
        CommandLineArguments arguments = CommandLineArguments.parse(new String[] {
            "--env=dev",
            "--pnr=ABC123",
            "--output=/downloads"
        });

        assertEquals("dev", arguments.environment());
        assertEquals("ABC123", arguments.pnr());
        assertEquals(Path.of("/downloads"), arguments.outputDirectory());
        assertEquals(Path.of("config", "application-dev.properties"), arguments.configPath());
    }

    @Test
    void parsesCustomConfigPath() {
        CommandLineArguments arguments = CommandLineArguments.parse(new String[] {
            "--env=prd",
            "--pnr=ABC123",
            "--output=/downloads",
            "--config=/opt/pnr/application.properties"
        });

        assertEquals(Path.of("/opt/pnr/application.properties"), arguments.configPath());
    }

    @Test
    void rejectsMissingEnvironment() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            CommandLineArguments.parse(new String[] {
                "--pnr=ABC123",
                "--output=/downloads"
            })
        );
        assertEquals("Missing required argument: --env", exception.getMessage());
    }

    @Test
    void rejectsUnknownArgument() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            CommandLineArguments.parse(new String[] {
                "--env=dev",
                "--pnr=ABC123",
                "--output=/downloads",
                "--extra=value"
            })
        );
        assertEquals("Unknown argument: --extra", exception.getMessage());
    }
}
