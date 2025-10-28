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

public class BookMapper {

    private final Map<Integer, Book> booksByNumber = new HashMap<>();
    private final Map<String, Book> booksByName = new HashMap<>();

    public BookMapper(String resourcePath) {
        try (InputStream is = BookMapper.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            // Refactored to use common loading logic
            loadFromInputStream(is);
        } catch (IOException e) {
            // In a real application, you might want more robust error handling.
            e.printStackTrace();
        }
    }

    public BookMapper(Map<String, List<String>> abbreviations) {
        loadMapping(abbreviations);
    }

    // --- FIX: Add the missing constructor that accepts an InputStream ---
    public BookMapper(InputStream inputStream) throws IOException {
        loadFromInputStream(inputStream);
    }

    /**
     * Helper method to load mappings from any InputStream.
     */
    private void loadFromInputStream(InputStream inputStream) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(inputStream)) {
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
                    // The rest of the list contains the short names/abbreviations.
                    List<String> shortNames = new ArrayList<>(names.subList(1, names.size()));

                    Book book = new Book(bookNumber, fullName, shortNames);
                    booksByNumber.put(bookNumber, book);

                    // Map all names (full and short) to the book object for easy lookup.
                    booksByName.put(fullName.toLowerCase(), book);
                    for (String name : shortNames) {
                        booksByName.put(name.toLowerCase(), book);
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
        return Optional.ofNullable(booksByName.get(name.toLowerCase()));
    }
}
