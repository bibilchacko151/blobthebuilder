package k15labs.in.blobthebuilder;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record CommandLineArguments(String environment, String pnr, Path outputDirectory, Path configPath) {
    private static final Set<String> REQUIRED_FLAGS = Set.of("env", "pnr", "output");
    private static final Set<String> ALLOWED_FLAGS = Set.of("env", "pnr", "output", "config");

    public static CommandLineArguments parse(String[] args) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                throw new IllegalArgumentException("Invalid argument format: " + arg);
            }
            int equalsIndex = arg.indexOf('=');
            String key = arg.substring(2, equalsIndex).trim();
            String value = arg.substring(equalsIndex + 1).trim();
            if (key.isEmpty()) {
                throw new IllegalArgumentException("Invalid argument format: " + arg);
            }
            if (!ALLOWED_FLAGS.contains(key)) {
                throw new IllegalArgumentException("Unknown argument: --" + key);
            }
            values.put(key, value);
        }

        for (String required : REQUIRED_FLAGS) {
            if (!values.containsKey(required) || values.get(required).isBlank()) {
                throw new IllegalArgumentException("Missing required argument: --" + required);
            }
        }

        String environment = values.get("env");
        String pnr = values.get("pnr");
        Path outputDirectory = Path.of(values.get("output"));
        Path configPath = values.containsKey("config") && !values.get("config").isBlank()
            ? Path.of(values.get("config"))
            : Path.of("config", "application-" + environment + ".properties");

        if (values.containsKey("config") && values.get("config").isBlank()) {
            throw new IllegalArgumentException("Invalid argument format: --config=");
        }

        return new CommandLineArguments(environment, pnr, outputDirectory, configPath);
    }
}
