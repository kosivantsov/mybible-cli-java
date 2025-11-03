package org.truetranslation.mybible.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.truetranslation.mybible.core.ConfigManager;

import java.awt.Color;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GuiThemeManager {

    private final Path themesDirectory;

    public GuiThemeManager() {
        // Use the core ConfigManager to find the root config directory
        ConfigManager coreConfig = new ConfigManager();
        this.themesDirectory = coreConfig.getDefaultConfigDir().resolve("gui_themes");
        try {
            // Ensure the gui_themes directory exists
            Files.createDirectories(themesDirectory);
        } catch (IOException e) {
            System.err.println("Could not create GUI themes directory: " + e.getMessage());
        }
    }

    /**
     * Saves the given GuiConfig object to a file named <themeName>.json.
     * @param themeName The name for the theme (e.g., "My Custom Dark")
     * @param config The GuiConfig object to save.
     */
    public void saveTheme(String themeName, GuiConfig config) throws IOException {
        Path themeFile = themesDirectory.resolve(themeName + ".json");
        try (Writer writer = Files.newBufferedWriter(themeFile)) {
            // Use registerTypeHierarchyAdapter to handle all Color instances, even nested ones.
            Gson gson = new GsonBuilder()
                .registerTypeHierarchyAdapter(Color.class, new ColorAdapter())
                .setPrettyPrinting()
                .create();
            gson.toJson(config, writer);
        }
    }

    /**
     * Loads a GuiConfig from a file named <themeName>.json.
     * @param themeName The name of the theme to load.
     * @return The loaded GuiConfig, or null if an error occurs.
     */
    public GuiConfig loadTheme(String themeName) throws IOException {
        Path themeFile = themesDirectory.resolve(themeName + ".json");
        if (!Files.exists(themeFile)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(themeFile)) {
            // Use registerTypeHierarchyAdapter to handle all Color instances, even nested ones.
            Gson gson = new GsonBuilder()
                .registerTypeHierarchyAdapter(Color.class, new ColorAdapter())
                .create();
            return gson.fromJson(reader, GuiConfig.class);
        }
    }
    
    /**
     * Returns a list of all available theme names by scanning the directory.
     * @return A list of theme names (without the .json extension).
     */
    public List<String> getAvailableThemes() {
        try (Stream<Path> files = Files.list(themesDirectory)) {
            return files
                .filter(f -> f.toString().endsWith(".json"))
                .map(f -> f.getFileName().toString().replace(".json", ""))
                .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Could not list GUI themes: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}
