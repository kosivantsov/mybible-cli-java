package org.truetranslation.mybible.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.truetranslation.mybible.core.ConfigManager;
import org.truetranslation.mybible.core.ExternalResourceBundleLoader;

import javax.swing.JOptionPane;
import java.awt.Color;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GuiThemeManager {

    private final Path themesDirectory;
    private final ResourceBundle bundle;

    public GuiThemeManager() {
        // Use the core ConfigManager to find the root config directory
        ConfigManager coreConfig = new ConfigManager();
        this.themesDirectory = coreConfig.getDefaultConfigDir().resolve("gui_themes");

        // Use ExternalResourceBundleLoader to support external i18n files
        ExternalResourceBundleLoader externalLoader = new ExternalResourceBundleLoader(
            coreConfig.getDefaultConfigDir()
        );
        this.bundle = externalLoader.getBundle("i18n.gui");

        try {
            // Ensure the gui_themes directory exists
            Files.createDirectories(themesDirectory);
        } catch (IOException e) {
            System.err.println("Could not create GUI themes directory: " + e.getMessage());
        }
    }

    /**
     * Saves the given GuiConfig object to a file named <themeName>.json.
     * If the theme already exists, prompts for confirmation.
     * @param themeName The name for the theme (e.g., "My Custom Dark")
     * @param config The GuiConfig object to save.
     * @return true if the theme was saved, false if the user cancelled.
     */
    public boolean saveTheme(String themeName, GuiConfig config) throws IOException {
        Path themeFile = themesDirectory.resolve(themeName + ".json");

        // Check if theme already exists
        if (Files.exists(themeFile)) {
            int result = JOptionPane.showConfirmDialog(
                null,
                bundle.getString("dialog.theme.overwritePrompt") + " \"" + themeName + "\"?",
                bundle.getString("dialog.theme.overwriteTitle"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );

            if (result != JOptionPane.YES_OPTION) {
                return false;
            }
        }

        try (Writer writer = Files.newBufferedWriter(themeFile)) {
            // Use registerTypeHierarchyAdapter to handle all Color instances, even nested ones.
            Gson gson = new GsonBuilder()
                .registerTypeHierarchyAdapter(Color.class, new ColorAdapter())
                .setPrettyPrinting()
                .create();
            gson.toJson(config, writer);
        }

        return true;
    }

    /**
     * Saves the given GuiConfig object without confirmation.
     * Useful for programmatic saves where confirmation is not needed.
     * @param themeName The name for the theme.
     * @param config The GuiConfig object to save.
     */
    public void saveThemeForce(String themeName, GuiConfig config) throws IOException {
        Path themeFile = themesDirectory.resolve(themeName + ".json");
        try (Writer writer = Files.newBufferedWriter(themeFile)) {
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

    /**
     * Deletes a theme by name.
     * @param themeName The name of the theme to delete.
     * @return true if deleted successfully, false otherwise.
     */
    public boolean deleteTheme(String themeName) {
        try {
            Path themeFile = themesDirectory.resolve(themeName + ".json");
            return Files.deleteIfExists(themeFile);
        } catch (IOException e) {
            System.err.println("Could not delete theme: " + e.getMessage());
            return false;
        }
    }
}
