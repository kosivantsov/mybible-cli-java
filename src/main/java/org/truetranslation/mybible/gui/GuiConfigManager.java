package org.truetranslation.mybible.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.Color;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GuiConfigManager {
    private final Path configFilePath;
    private GuiConfig config;
    private final Gson gson;

    public GuiConfigManager() {
        this.configFilePath = getConfigPath();
        
        // Register the custom TypeAdapter for Color
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Color.class, new ColorTypeAdapter())
                .setPrettyPrinting()
                .create();
        
        loadConfig();
    }

    private Path getConfigPath() {
        String userHome = System.getProperty("user.home");
        Path configDir;
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            configDir = Paths.get(System.getenv("APPDATA"), "mybible-cli-java");
        } else if (os.contains("mac")) {
            configDir = Paths.get(userHome, "Library", "Application Support", "mybible-cli-java");
        } else {
            configDir = Paths.get(userHome, ".config", "mybible-cli-java");
        }
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            System.err.println("Could not create config directory: " + configDir);
            e.printStackTrace();
        }
        return configDir.resolve("gui.json");
    }

    private void loadConfig() {
        try (FileReader reader = new FileReader(configFilePath.toFile())) {
            config = gson.fromJson(reader, GuiConfig.class);
            if (config == null) {
                config = new GuiConfig();
            }
        } catch (IOException e) {
            config = new GuiConfig(); // Create default config if file doesn't exist
        }
    }

    public void saveConfig() {
        try (FileWriter writer = new FileWriter(configFilePath.toFile())) {
            gson.toJson(config, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public GuiConfig getConfig() {
        return config;
    }
}
