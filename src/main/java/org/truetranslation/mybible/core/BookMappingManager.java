package org.truetranslation.mybible.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class BookMappingManager {

    private static final String DEFAULT_MAPPING_FILENAME = "default_mapping.json";

    private static final LocalizationManager loc = LocalizationManager.getInstance();

    /**
     * Gets the default BookMapper by ensuring default_mapping.json exists.
     * This is a convenience method that calls the main loader with a null prefix.
     */
    public static BookMapper getBookMapper(ConfigManager configManager) throws IOException {
        return getBookMapper(configManager, null);
    }

    /**
     * Gets a BookMapper, trying a custom mapping file first and falling back to the default.
     *
     * @param configManager The application's ConfigManager.
     * @param prefix The prefix for a custom mapping file. If null, the default is used.
     * @return A BookMapper instance.
     * @throws IOException If the default mapping file cannot be read or copied.
     */
    public static BookMapper getBookMapper(ConfigManager configManager, String prefix) throws IOException {
        Path configDir = configManager.getDefaultConfigDir();
        Path mappingFile = null;
        int verbosity = configManager.getVerbosity();

        if (prefix != null && !prefix.trim().isEmpty()) {
            // A custom prefix is provided. Attempt to use the custom file.
            Path customMappingFile = configDir.resolve(prefix + "_mapping.json");
            if (Files.exists(customMappingFile)) {
                mappingFile = customMappingFile;
            } else {
                if (verbosity > 0) {
                    System.out.println(loc.getString("info.mapping.fallback", customMappingFile.getFileName().toString()));
                }
            }
        }

        // If no custom file was found (or none was specified), use the default.
        if (mappingFile == null) {
            mappingFile = configDir.resolve(DEFAULT_MAPPING_FILENAME);
            if (!Files.exists(mappingFile)) {
                // Copy the default from resources if it's missing.
                Files.createDirectories(configDir);
                try (InputStream resourceStream = BookMappingManager.class.getResourceAsStream("/" + DEFAULT_MAPPING_FILENAME)) {
                    if (resourceStream == null) {
                        throw new IOException(loc.getString("error.mapping.notFoundInResources", DEFAULT_MAPPING_FILENAME));
                    }
                    Files.copy(resourceStream, mappingFile, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        // Load the mapper from the determined file path.
        return new BookMapper(Files.newInputStream(mappingFile));
    }
}
