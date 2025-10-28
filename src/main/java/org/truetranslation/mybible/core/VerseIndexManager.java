package org.truetranslation.mybible.core;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
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
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class VerseIndexManager {

    private final Path moduleDataDir;
    private static final Gson GSON = new Gson();
    private final Map<String, Map<Integer, Integer>> indexCache = new ConcurrentHashMap<>();
    private final LocalizationManager loc = LocalizationManager.getInstance();
    private final int verbosity;

    public VerseIndexManager(ConfigManager configManager, int verbosity) {
        this.moduleDataDir = configManager.getDefaultConfigDir().resolve("moduledata");
        this.verbosity = verbosity;
    }

    public Map<Integer, Integer> getVerseIndex(String moduleName, Path modulePath) {
        if (indexCache.containsKey(moduleName)) {
            return indexCache.get(moduleName);
        }

        Path indexFile = moduleDataDir.resolve(moduleName + ".allverses.json");

        if (Files.exists(indexFile)) {
            try (FileReader reader = new FileReader(indexFile.toFile())) {
                Type type = new TypeToken<Map<Integer, Integer>>() {}.getType();
                Map<Integer, Integer> index = GSON.fromJson(reader, type);
                if (index != null) {
                    indexCache.put(moduleName, index);
                    return index;
                }
            } catch (JsonSyntaxException e) {
                System.err.println(loc.getString("msg.cache.corrupt"));
                try { Files.delete(indexFile); } catch (IOException ioException) {
                    System.err.println(loc.getString("msg.cache.deleteFailed", ioException.getMessage()));
                }
            } catch (IOException e) {
                System.err.println(loc.getString("msg.cache.readError", e.getMessage()));
            }
        }
        return generateAndCacheIndex(moduleName, modulePath);
    }
    
    private Map<Integer, Integer> generateAndCacheIndex(String moduleName, Path modulePath) {
        Map<Integer, TreeMap<Integer, Integer>> tempIndex = new TreeMap<>();
        String url = "jdbc:sqlite:" + modulePath.toAbsolutePath();
        String sql = "SELECT book_number, chapter, verse FROM verses";
        
        if (this.verbosity > 0) {
            System.out.println(loc.getString("msg.cache.generating", moduleName));
        }
        
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                int book = rs.getInt("book_number");
                int chapter = rs.getInt("chapter");
                int verse = rs.getInt("verse");
                tempIndex.computeIfAbsent(book, k -> new TreeMap<>()).merge(chapter, verse, Integer::max);
            }
        } catch (SQLException e) {
            System.err.println(loc.getString("error.unexpected") + " " + e.getMessage());
            return new ConcurrentHashMap<>();
        }
        
        if (this.verbosity > 0) {
            System.out.println(loc.getString("msg.cache.complete"));
        }

        Map<Integer, Integer> finalIndex = new TreeMap<>();
        for (Map.Entry<Integer, TreeMap<Integer, Integer>> bookEntry : tempIndex.entrySet()) {
            for (Map.Entry<Integer, Integer> chapterEntry : bookEntry.getValue().entrySet()) {
                finalIndex.put(bookEntry.getKey() * 1000 + chapterEntry.getKey(), chapterEntry.getValue());
            }
        }

        try {
            Files.createDirectories(moduleDataDir);
            Path indexFile = moduleDataDir.resolve(moduleName + ".allverses.json");
            try (FileWriter writer = new FileWriter(indexFile.toFile())) {
                GSON.toJson(finalIndex, writer);
            }
        } catch (IOException e) {
            System.err.println("Error saving verse index file: " + e.getMessage());
        }

        indexCache.put(moduleName, finalIndex);
        return finalIndex;
    }
}
