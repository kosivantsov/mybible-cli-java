package org.truetranslation.mybible.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

public class ExternalResourceBundleLoader {
    
    private final Path configDir;
    
    public ExternalResourceBundleLoader(Path configDir) {
        this.configDir = configDir;
    }
    
    // First check external config directory, then fall back to classpath resources.
    public ResourceBundle getBundle(String baseName, Locale locale) {
        ResourceBundle externalBundle = loadExternalBundle(baseName, locale);
        if (externalBundle != null) {
            return externalBundle;
        }
        
        return ResourceBundle.getBundle(baseName, locale);
    }
    
    public ResourceBundle getBundle(String baseName) {
        return getBundle(baseName, Locale.getDefault());
    }
    
    private ResourceBundle loadExternalBundle(String baseName, Locale locale) {
        String[] filenames = buildFilenames(baseName, locale);
        
        for (String filename : filenames) {
            Path bundlePath = configDir.resolve("resources").resolve(filename.replace('.', '/') + ".properties");
            
            if (Files.exists(bundlePath)) {
                try (InputStream is = Files.newInputStream(bundlePath);
                     InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    return new PropertyResourceBundle(reader);
                } catch (IOException e) {
                    System.err.println("Failed to load external bundle: " + bundlePath + " - " + e.getMessage());
                }
            }
        }
        
        return null;
    }
    
    private String[] buildFilenames(String baseName, Locale locale) {
        String language = locale.getLanguage();
        String country = locale.getCountry();
        
        if (!language.isEmpty() && !country.isEmpty()) {
            return new String[] {
                baseName + "_" + language + "_" + country,
                baseName + "_" + language,
                baseName
            };
        } else if (!language.isEmpty()) {
            return new String[] {
                baseName + "_" + language,
                baseName
            };
        } else {
            return new String[] { baseName };
        }
    }
}
