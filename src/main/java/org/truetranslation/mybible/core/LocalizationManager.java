package org.truetranslation.mybible.core;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class LocalizationManager {
    private static LocalizationManager instance;
    private final ResourceBundle bundle;

    public LocalizationManager() {
        ResourceBundle tempBundle;
        try {
            tempBundle = ResourceBundle.getBundle("messages", Locale.getDefault());
        } catch (MissingResourceException e) {
            System.err.println("Warning: No localization file found for locale '" + Locale.getDefault() + "'. Falling back to default.");
            tempBundle = ResourceBundle.getBundle("messages", Locale.ROOT);
        }
        this.bundle = tempBundle;
    }

    public String getString(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    public String getString(String key, Object... args) {
        return MessageFormat.format(getString(key), args);
    }

    public static synchronized LocalizationManager getInstance() {
        if (instance == null) {
            instance = new LocalizationManager();
        }
        return instance;
    }
}
