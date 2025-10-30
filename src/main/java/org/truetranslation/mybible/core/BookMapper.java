package org.truetranslation.mybible.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.truetranslation.mybible.core.model.Book;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public class BookMapper {

    private final Map<Integer, Book> booksByNumber = new HashMap<>();
    private final Map<String, Book> booksByName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public BookMapper(String resourcePath) {
        try (InputStream is = BookMapper.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            loadFromInputStream(is);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // This constructor now correctly handles the Map<String, List<String>> type
    public BookMapper(Map<String, List<String>> abbreviations) {
        loadMapping(abbreviations);
    }

    public BookMapper(InputStream inputStream) throws IOException {
        loadFromInputStream(inputStream);
    }

    private void loadFromInputStream(InputStream inputStream) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(inputStream)) {
            // The JSON is a map of String (book number) to a list of String names
            Type type = new TypeToken<Map<String, List<String>>>() {}.getType();
            Map<String, List<String>> abbreviations = new Gson().fromJson(reader, type);
            loadMapping(abbreviations);
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
