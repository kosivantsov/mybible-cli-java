package org.truetranslation.mybible.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class ConfigManager {
    private final Path configFilePath;
    private Map<String, Object> config;
    private static final String DEFAULT_FORMAT = "%a %c:%v %t";
    private static final ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages");

    public ConfigManager() {
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
        this.configFilePath = configDir.resolve("config.json");
        loadConfig();
    }

    private void loadConfig() {
        try {
            if (Files.exists(configFilePath)) {
                Reader reader = Files.newBufferedReader(configFilePath);
                Type type = new TypeToken<Map<String, Object>>() {}.getType();
                config = new Gson().fromJson(reader, type);
                reader.close();
                if (config == null) config = new HashMap<>();
            } else {
                config = new HashMap<>();
                saveConfig();
            }
        } catch (IOException e) {
            config = new HashMap<>();
        }
        // Ensure default values are present
        config.putIfAbsent("modules_path", "");
        config.putIfAbsent("format_string", DEFAULT_FORMAT);
        config.putIfAbsent("last_used_module", "");
        config.putIfAbsent("verbosity", 1.0);
    }

    private void saveConfig() {
        try {
            Files.createDirectories(configFilePath.getParent());
            Writer writer = Files.newBufferedWriter(configFilePath);
            new Gson().toJson(config, writer);
            writer.close();
        } catch (IOException e) {
            String message = MessageFormat.format(bundle.getString("error.config.save"), e.getMessage());
            System.err.println(message);
        }
    }

    public String getModulesPath() { return (String) config.get("modules_path"); }
    public void setModulesPath(String path) { config.put("modules_path", path); saveConfig(); }
    public String getFormatString() { return (String) config.get("format_string"); }
    public void setFormatString(String format) { config.put("format_string", format); saveConfig(); }
    public String getLastUsedModule() { return (String) config.get("last_used_module"); }
    public void setLastUsedModule(String moduleName) { config.put("last_used_module", moduleName); saveConfig(); }
    public Path getDefaultConfigDir() { return configFilePath.getParent(); }

    public int getVerbosity() {
        return ((Double) config.getOrDefault("verbosity", 1.0)).intValue();
    }
    public void setVerbosity(int level) {
        config.put("verbosity", (double) level);
        saveConfig();
    }
}
