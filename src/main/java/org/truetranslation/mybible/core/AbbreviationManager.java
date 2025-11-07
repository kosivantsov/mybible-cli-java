package org.truetranslation.mybible.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class AbbreviationManager {

    private final Path moduleDataDir;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final ResourceBundle bundle;
    private final int verbosity;

    public AbbreviationManager(ConfigManager configManager, int verbosity) {
        this.moduleDataDir = configManager.getDefaultConfigDir().resolve("moduledata");
        ExternalResourceBundleLoader externalLoader = new ExternalResourceBundleLoader(
            configManager.getDefaultConfigDir()
        );
        this.bundle = externalLoader.getBundle("i18n.messages");
        this.verbosity = verbosity;
    }

    public Path ensureAbbreviationFile(String moduleName, Path modulePath) throws SQLException, IOException {
        Path abbrFile = moduleDataDir.resolve(moduleName + ".abbr.json");
        if (Files.exists(abbrFile)) {
            return abbrFile;
        }

        if (this.verbosity > 0) {
            String message = MessageFormat.format(bundle.getString("msg.cache.generatingAbbrs"), moduleName);
            System.out.println(message);
        }
        extractAbbreviations(modulePath, abbrFile);

        if (this.verbosity > 0) {
            String message = MessageFormat.format(bundle.getString("msg.cache.completeAbbrs"), abbrFile);
            System.out.println(message);
        }
        return abbrFile;
    }

    private void extractAbbreviations(Path modulePath, Path outputPath) throws SQLException, IOException {
        Map<String, List<String>> abbreviations = new HashMap<>();
        String url = "jdbc:sqlite:" + modulePath.toAbsolutePath();
        String sql = "SELECT book_number, long_name, short_name FROM books";

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                abbreviations.put(rs.getString("book_number"), List.of(rs.getString("long_name"), rs.getString("short_name")));
            }
        }

        Files.createDirectories(outputPath.getParent());
        try (FileWriter writer = new FileWriter(outputPath.toFile())) {
            GSON.toJson(abbreviations, writer);
        }
    }

    public Map<String, List<String>> loadAbbreviations(Path abbrFilePath) throws IOException {
         try (FileReader reader = new FileReader(abbrFilePath.toFile())) {
            Type type = new TypeToken<Map<String, List<String>>>() {}.getType();
            return GSON.fromJson(reader, type);
        }
    }
}
