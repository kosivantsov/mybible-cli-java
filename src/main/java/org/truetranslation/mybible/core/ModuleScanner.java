package org.truetranslation.mybible.core;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Scans a directory for MyBible SQLite modules, filters them, and retrieves their metadata.
 */
public class ModuleScanner {

    // List of substrings used to filter out non-Bible modules.
    private static final List<String> EXCLUDED_SUBSTRINGS = Arrays.asList(
            "commentaries", "cross-references", "crossreferences", "devotions",
            "dictionaries_lookup", "dictionaries-lookup", "dictionary", "plan",
            "referencedata", "subheadings"
    );


    /**
     * A simple data class representing a MyBible module's metadata.
     */
    public static class Module {
        private final String language;
        private final String name;
        private final String description;
        private final Path path;

        public Module(String language, String name, String description, Path path) {
            this.language = language;
            this.name = name;
            this.description = description;
            this.path = path;
        }

        public String getLanguage() { return language; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public Path getPath() { return path; }
    }

    /**
     * Scans the directory, filters for Bible modules, and returns their metadata.
     * @param modulesDir The directory to scan.
     * @return A sorted list of filtered Module objects.
     * @throws IOException If the directory cannot be read.
     */
    public List<Module> findModules(Path modulesDir) throws IOException {
        List<Module> modules = new ArrayList<>();
        if (modulesDir == null || !Files.isDirectory(modulesDir)) {
            return modules;
        }
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modulesDir)) {
            for (Path entry : stream) {
                String fileNameLower = entry.getFileName().toString().toLowerCase();
                if (Files.isRegularFile(entry) && fileNameLower.endsWith(".sqlite3") && !isExcluded(fileNameLower)) {
                    getModuleInfo(entry).ifPresent(modules::add);
                }
            }
        }
        modules.sort(
            Comparator.comparing(Module::getLanguage, String.CASE_INSENSITIVE_ORDER)
                      .thenComparing(Module::getName, String.CASE_INSENSITIVE_ORDER)
        );
        return modules;
    }

    /**
     * Checks if a filename contains any of the excluded substrings.
     */
    private boolean isExcluded(String fileName) {
        for (String substring : EXCLUDED_SUBSTRINGS) {
            if (fileName.contains(substring)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts metadata and formats the description.
     */
    private Optional<Module> getModuleInfo(Path modulePath) {
        String url = "jdbc:sqlite:" + modulePath.toAbsolutePath().toString();
        String defaultName = modulePath.getFileName().toString().replaceAll("(?i)\\.sqlite3$", "");
        
        String language = getInfoField(url, "language").orElse("NA");
        String descriptionRaw = getInfoField(url, "description").orElse("NA");
        // NEW: Replace newline characters with " | " for cleaner single-line display.
        String description = descriptionRaw.replace("\n", " | ").replace("\r", ""); // Also remove carriage returns
        String name = getInfoField(url, "name").orElse(defaultName);
        
        return Optional.of(new Module(language, name, description, modulePath));
    }

    /**
     * Helper method to get a single field from the module's info table.
     * This tries multiple common column names for the key for maximum compatibility.
     */
    private Optional<String> getInfoField(String url, String fieldName) {
        String[] keyColumns = {"key", "name"};
        for (String keyColumn : keyColumns) {
            String sql = "SELECT value FROM info WHERE " + keyColumn + " = ?";
            try (Connection conn = DriverManager.getConnection(url);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                
                pstmt.setString(1, fieldName);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.ofNullable(rs.getString("value"));
                    }
                }
            } catch (SQLException e) {
                // Ignore and try the next key column name, as this can be an expected failure.
            }
        }
        return Optional.empty();
    }
}
