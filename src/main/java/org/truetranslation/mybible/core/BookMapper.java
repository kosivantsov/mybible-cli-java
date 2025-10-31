package org.truetranslation.mybible.core;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.truetranslation.mybible.core.model.Book;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.TreeMap;

public class BookMapper {

    private final Map<Integer, Book> booksByNumber = new HashMap<>();
    private final Map<String, Book> booksByName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private static final ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages");

    public BookMapper(String resourcePath) {
        try (InputStream is = BookMapper.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                String message = MessageFormat.format(bundle.getString("error.mapping.resourceNotFound"), resourcePath);
                throw new IOException(message);
            }
            loadFromInputStream(is);
        } catch (IOException e) {
            String message = MessageFormat.format(bundle.getString("error.mapping.resourceLoadFailed"), resourcePath, e.getMessage());
            System.err.println(message);
            e.printStackTrace();
        }
    }

    public BookMapper(Map<String, List<String>> abbreviations) {
        loadMapping(abbreviations);
    }

    public BookMapper(InputStream inputStream) throws IOException {
        try {
            loadFromInputStream(inputStream);
        } catch (JsonSyntaxException | IllegalStateException e) {
            // Log the error message
            System.err.println(bundle.getString("error.mapping.invalidFormat"));
            System.err.println(bundle.getString("info.mapping.usingDefault"));
            
            // Load default mapping as fallback
            loadDefaultMapping();
        }
    }

    private void loadFromInputStream(InputStream inputStream) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(inputStream)) {
            // The JSON is a map of String (book number) to a list of String names
            Type type = new TypeToken<Map<String, List<String>>>() {}.getType();
            Map<String, List<String>> abbreviations = new Gson().fromJson(reader, type);
            loadMapping(abbreviations);
        }
    }

    private void loadDefaultMapping() {
        try (InputStream defaultStream = BookMapper.class.getResourceAsStream("/default_mapping.json")) {
            if (defaultStream != null) {
                loadFromInputStream(defaultStream);
            } else {
                System.err.println(bundle.getString("error.mapping.defaultNotFound"));
            }
        } catch (IOException e) {
            String message = MessageFormat.format(bundle.getString("error.mapping.defaultLoadFailed"), e.getMessage());
            System.err.println(message);
        }
    }

    /*
     * Static method to validate if a mapping file has valid JSON format
     * @param inputStream The input stream to validate
     * @return true if valid, false if invalid
     */
    public static boolean isValidMappingFile(InputStream inputStream) {
        try (InputStreamReader reader = new InputStreamReader(inputStream)) {
            Type type = new TypeToken<Map<String, List<String>>>() {}.getType();
            Map<String, List<String>> result = new Gson().fromJson(reader, type);
            return result != null; // Basic validation - not null
        } catch (JsonSyntaxException | IllegalStateException | IOException e) {
            return false;
        }
    }

    private void loadMapping(Map<String, List<String>> abbreviations) {
        if (abbreviations == null) return;
        for (Map.Entry<String, List<String>> entry : abbreviations.entrySet()) {
            try {
                int bookNumber = Integer.parseInt(entry.getKey());
                List<String> names = entry.getValue();
                if (names != null && !names.isEmpty()) {
                    String fullName = names.get(0);
                    // Use the rest of the list for short names/aliases
                    List<String> allNamesAndAliases = new ArrayList<>(names);

                    Book book = new Book(bookNumber, fullName, allNamesAndAliases);
                    booksByNumber.put(bookNumber, book);

                    // The TreeMap handles case-insensitivity automatically.
                    for (String name : allNamesAndAliases) {
                        booksByName.put(name.trim(), book);
                    }
                }
            } catch (NumberFormatException e) {
                // Silently ignore entries with non-numeric keys.
            }
        }
    }

    public Optional<Book> getBook(int bookNumber) {
        return Optional.ofNullable(booksByNumber.get(bookNumber));
    }

    public Optional<Book> getBook(String name) {
        // The lookup is inherently case-insensitive.
        return Optional.ofNullable(booksByName.get(name));
    }
}
