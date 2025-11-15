package org.truetranslation.mybible.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonSyntaxException;
import org.truetranslation.mybible.core.model.Book;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.TreeMap;

@SuppressWarnings("unchecked")
public class BookMapper {

    private final Map<Integer, Book> booksByNumber = new HashMap<>();
    private final Map<String, Book> booksByName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    // Store the raw language-aware mapping data
    private final Map<Integer, Map<String, Object>> languageAwareMapping = new HashMap<>();

    // User-specified language and module language for name lookup
    private final String userLanguage;
    private final String moduleLanguage;

    private static final ConfigManager configManager = new ConfigManager();
    private static ExternalResourceBundleLoader externalLoader = new ExternalResourceBundleLoader(
        configManager.getDefaultConfigDir()
    );

    private static ResourceBundle bundle = externalLoader.getBundle("i18n.messages");

    // Default language used when no specific language is provided
    private static final String DEFAULT_FALLBACK = "default";

    public BookMapper(String resourcePath) {
        this(resourcePath, null, null);
    }

    public BookMapper(String resourcePath, String userLanguage) {
        this(resourcePath, userLanguage, null);
    }

    public BookMapper(String resourcePath, String userLanguage, String moduleLanguage) {
        this.userLanguage = userLanguage;
        this.moduleLanguage = moduleLanguage;
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
        this.userLanguage = null;
        this.moduleLanguage = null;
        loadMapping(abbreviations);
    }

    public BookMapper(InputStream inputStream) throws IOException {
        this(inputStream, null, null);
    }

    public BookMapper(InputStream inputStream, String userLanguage) throws IOException {
        this(inputStream, userLanguage, null);
    }

    public BookMapper(InputStream inputStream, String userLanguage, String moduleLanguage) throws IOException {
        this.userLanguage = userLanguage;
        this.moduleLanguage = moduleLanguage;
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
            JsonElement rootElement = new Gson().fromJson(reader, JsonElement.class);

            if (rootElement.isJsonObject()) {
                // New language-aware format
                JsonObject jsonObject = rootElement.getAsJsonObject();
                loadLanguageAwareMapping(jsonObject);
            } else {
                // Old format - try to parse as legacy format
                reader.close();
                // Reopen stream for legacy parsing
                try (InputStream legacyStream = BookMapper.class.getResourceAsStream("/default_mapping.json");
                     InputStreamReader legacyReader = new InputStreamReader(legacyStream)) {

                    com.google.gson.reflect.TypeToken<Map<String, List<String>>> typeToken = 
                        new com.google.gson.reflect.TypeToken<Map<String, List<String>>>() {};
                    Map<String, List<String>> abbreviations = new Gson().fromJson(legacyReader, typeToken.getType());
                    loadMapping(abbreviations);
                }
            }
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

    private void loadLanguageAwareMapping(JsonObject jsonObject) {
        if (jsonObject == null) return;

        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            try {
                int bookNumber = Integer.parseInt(entry.getKey());
                JsonArray bookArray = entry.getValue().getAsJsonArray();

                if (bookArray != null && bookArray.size() > 0) {
                    Map<String, Object> bookData = parseBookData(bookArray);
                    languageAwareMapping.put(bookNumber, bookData);

                    // Create a Book object using default fallback names
                    List<String> fallbackNames = (List<String>) bookData.get(DEFAULT_FALLBACK);
                    if (fallbackNames != null && !fallbackNames.isEmpty()) {
                        String fullName = fallbackNames.get(0);
                        Book book = new Book(bookNumber, fullName, fallbackNames);
                        booksByNumber.put(bookNumber, book);

                        // Always register all fallback names for lookup
                        for (String name : fallbackNames) {
                            booksByName.put(name.trim(), book);
                        }

                        // Register module language names (if module language is specified)
                        if (moduleLanguage != null && !moduleLanguage.trim().isEmpty()) {
                            List<String> moduleLangNames = (List<String>) bookData.get(moduleLanguage);
                            if (moduleLangNames != null) {
                                for (String name : moduleLangNames) {
                                    booksByName.put(name.trim(), book);
                                }
                            }
                        }

                        // Register user language names (if user language is specified and different from module language)
                        if (userLanguage != null && !userLanguage.trim().isEmpty() 
                            && !userLanguage.equals(moduleLanguage)) {
                            List<String> userLangNames = (List<String>) bookData.get(userLanguage);
                            if (userLangNames != null) {
                                for (String name : userLangNames) {
                                    booksByName.put(name.trim(), book);
                                }
                            }
                        }
                    }
                }
            } catch (NumberFormatException | IllegalStateException e) {
                // Silently ignore entries with non-numeric keys or invalid structure
            }
        }
    }

    private Map<String, Object> parseBookData(JsonArray bookArray) {
        Map<String, Object> bookData = new HashMap<>();
        List<String> fallbackNames = new ArrayList<>();

        for (JsonElement element : bookArray) {
            if (element.isJsonPrimitive()) {
                // This is a fallback name
                fallbackNames.add(element.getAsString());
            } else if (element.isJsonObject()) {
                // This is a language-specific mapping
                JsonObject langObject = element.getAsJsonObject();
                for (Map.Entry<String, JsonElement> langEntry : langObject.entrySet()) {
                    String language = langEntry.getKey();
                    List<String> names = new ArrayList<>();

                    if (langEntry.getValue().isJsonArray()) {
                        JsonArray nameArray = langEntry.getValue().getAsJsonArray();
                        for (JsonElement nameElement : nameArray) {
                            if (nameElement.isJsonPrimitive()) {
                                names.add(nameElement.getAsString());
                            }
                        }
                    }

                    bookData.put(language, names);
                }
            }
        }

        bookData.put(DEFAULT_FALLBACK, fallbackNames);
        return bookData;
    }

    private void loadMapping(Map<String, List<String>> abbreviations) {
        if (abbreviations == null) return;
        for (Map.Entry<String, List<String>> entry : abbreviations.entrySet()) {
            try {
                int bookNumber = Integer.parseInt(entry.getKey());
                List<String> names = entry.getValue();
                if (names != null && !names.isEmpty()) {
                    String fullName = names.get(0);
                    List<String> allNamesAndAliases = new ArrayList<>(names);

                    Book book = new Book(bookNumber, fullName, allNamesAndAliases);
                    booksByNumber.put(bookNumber, book);

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

    /**
     * Get a book by number with four-tier language priority:
     * 1. User-specified language (if provided)
     * 2. Module language (if provided and different from user language)
     * 3. Default fallback names
     * 4. English names (if available and different from fallback)
     */
    public Optional<Book> getBook(int bookNumber, String userLanguage, String moduleLanguage) {
        Map<String, Object> bookData = languageAwareMapping.get(bookNumber);
        if (bookData == null) {
            return getBook(bookNumber);
        }

        // Priority 1: User-specified language
        if (userLanguage != null && !userLanguage.trim().isEmpty()) {
            List<String> userLanguageNames = (List<String>) bookData.get(userLanguage);
            if (userLanguageNames != null && !userLanguageNames.isEmpty()) {
                String fullName = userLanguageNames.get(0);
                return Optional.of(new Book(bookNumber, fullName, new ArrayList<>(userLanguageNames)));
            }
        }

        // Priority 2: Module language (if different from user language)
        if (moduleLanguage != null && !moduleLanguage.trim().isEmpty() && !moduleLanguage.equals(userLanguage)) {
            List<String> moduleLanguageNames = (List<String>) bookData.get(moduleLanguage);
            if (moduleLanguageNames != null && !moduleLanguageNames.isEmpty()) {
                String fullName = moduleLanguageNames.get(0);
                return Optional.of(new Book(bookNumber, fullName, new ArrayList<>(moduleLanguageNames)));
            }
        }

        // Priority 3: Default fallback names
        List<String> fallbackNames = (List<String>) bookData.get(DEFAULT_FALLBACK);
        if (fallbackNames != null && !fallbackNames.isEmpty()) {
            String fullName = fallbackNames.get(0);
            return Optional.of(new Book(bookNumber, fullName, new ArrayList<>(fallbackNames)));
        }

        // Priority 4: English names (if available and different from user/module languages)
        if (!"en".equals(userLanguage) && !"en".equals(moduleLanguage)) {
            List<String> englishNames = (List<String>) bookData.get("en");
            if (englishNames != null && !englishNames.isEmpty()) {
                String fullName = englishNames.get(0);
                return Optional.of(new Book(bookNumber, fullName, new ArrayList<>(englishNames)));
            }
        }

        return Optional.empty();
    }

    public Optional<Book> getBook(int bookNumber, String moduleLanguage) {
        return getBook(bookNumber, null, moduleLanguage);
    }

    public Optional<Book> getBook(String name) {
        return Optional.ofNullable(booksByName.get(name));
    }

    public List<String> getNamesForLanguage(int bookNumber, String language) {
        Map<String, Object> bookData = languageAwareMapping.get(bookNumber);
        if (bookData == null) {
            return new ArrayList<>();
        }

        List<String> names = (List<String>) bookData.get(language);
        return names != null ? new ArrayList<>(names) : new ArrayList<>();
    }

    public String getPrimaryName(int bookNumber, String userLanguage, String moduleLanguage) {
        Map<String, Object> bookData = languageAwareMapping.get(bookNumber);
        if (bookData == null) {
            return "";
        }

        // Priority 1: User-specified language
        if (userLanguage != null && !userLanguage.trim().isEmpty()) {
            List<String> userLanguageNames = (List<String>) bookData.get(userLanguage);
            if (userLanguageNames != null && !userLanguageNames.isEmpty()) {
                return userLanguageNames.get(0);
            }
        }

        // Priority 2: Module language
        if (moduleLanguage != null && !moduleLanguage.trim().isEmpty() && !moduleLanguage.equals(userLanguage)) {
            List<String> moduleLanguageNames = (List<String>) bookData.get(moduleLanguage);
            if (moduleLanguageNames != null && !moduleLanguageNames.isEmpty()) {
                return moduleLanguageNames.get(0);
            }
        }

        // Priority 3: Default fallback
        List<String> fallbackNames = (List<String>) bookData.get(DEFAULT_FALLBACK);
        if (fallbackNames != null && !fallbackNames.isEmpty()) {
            return fallbackNames.get(0);
        }

        // Priority 4: English names
        if (!"en".equals(userLanguage) && !"en".equals(moduleLanguage)) {
            List<String> englishNames = (List<String>) bookData.get("en");
            if (englishNames != null && !englishNames.isEmpty()) {
                return englishNames.get(0);
            }
        }
        return "";
    }

    public String getPrimaryName(int bookNumber, String moduleLanguage) {
        return getPrimaryName(bookNumber, null, moduleLanguage);
    }

    public String getPrimaryAbbreviation(int bookNumber, String userLanguage, String moduleLanguage) {
        Map<String, Object> bookData = languageAwareMapping.get(bookNumber);
        if (bookData == null) {
            return "";
        }

        // Priority 1: User-specified language
        if (userLanguage != null && !userLanguage.trim().isEmpty()) {
            List<String> userLanguageNames = (List<String>) bookData.get(userLanguage);
            if (userLanguageNames != null && userLanguageNames.size() > 1) {
                return userLanguageNames.get(1); // Second name is typically the abbreviation
            }
        }

        // Priority 2: Module language
        if (moduleLanguage != null && !moduleLanguage.trim().isEmpty() && !moduleLanguage.equals(userLanguage)) {
            List<String> moduleLanguageNames = (List<String>) bookData.get(moduleLanguage);
            if (moduleLanguageNames != null && moduleLanguageNames.size() > 1) {
                return moduleLanguageNames.get(1);
            }
        }

        // Priority 3: Default fallback
        List<String> fallbackNames = (List<String>) bookData.get(DEFAULT_FALLBACK);
        if (fallbackNames != null && fallbackNames.size() > 1) {
            return fallbackNames.get(1);
        } else if (fallbackNames != null && !fallbackNames.isEmpty()) {
            return fallbackNames.get(0);
        }

        // Priority 4: English names
        if (!"en".equals(userLanguage) && !"en".equals(moduleLanguage)) {
            List<String> englishNames = (List<String>) bookData.get("en");
            if (englishNames != null && englishNames.size() > 1) {
                return englishNames.get(1);
            } else if (englishNames != null && !englishNames.isEmpty()) {
                return englishNames.get(0);
            }
        }

        return "";
    }

    public String getPrimaryAbbreviation(int bookNumber, String moduleLanguage) {
        return getPrimaryAbbreviation(bookNumber, null, moduleLanguage);
    }

    // Extract language from a module SQLite file
    public static String extractModuleLanguage(Path modulePath) {
        String url = "jdbc:sqlite:" + modulePath.toAbsolutePath().toString();
        String[] keyColumns = {"key", "name"};

        for (String keyColumn : keyColumns) {
            String sql = "SELECT value FROM info WHERE " + keyColumn + " = ?";
            try (Connection conn = DriverManager.getConnection(url);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, "language");
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        String language = rs.getString("value");
                        return (language != null && !language.trim().isEmpty()) ? language.trim() : "en";
                    }
                }
            } catch (SQLException e) {
                // Continue to next key column
            }
        }
        return "en";
    }

    public List<String> getAllBookNames(int bookNumber, BookMapper moduleBookMapper, String moduleLanguage, String userLanguage) {
        List<String> allNames = new ArrayList<>();

        // Add default names
        Optional<Book> defaultBook = this.getBook(bookNumber);
        if (defaultBook.isPresent()) {
            allNames.addAll(defaultBook.get().getShortNames());
        }

        // Add module names
        if (moduleBookMapper != null) {
            Optional<Book> moduleBook = moduleBookMapper.getBook(bookNumber);
            if (moduleBook.isPresent()) {
                for (String name : moduleBook.get().getShortNames()) {
                    if (!allNames.contains(name)) {
                        allNames.add(name);
                    }
                }
            }
        }

        // Add module language names
        if (moduleLanguage != null && !moduleLanguage.trim().isEmpty()) {
            List<String> moduleLangNames = this.getNamesForLanguage(bookNumber, moduleLanguage);
            for (String name : moduleLangNames) {
                if (!allNames.contains(name)) {
                    allNames.add(name);
                }
            }
        }

        // Add user language names
        if (userLanguage != null && !userLanguage.trim().isEmpty()) {
            List<String> userLangNames = this.getNamesForLanguage(bookNumber, userLanguage);
            for (String name : userLangNames) {
                if (!allNames.contains(name)) {
                    allNames.add(name);
                }
            }
        }
        return allNames;
    }

    // Validate if a mapping file has valid JSON format
    public static boolean isValidMappingFile(InputStream inputStream) {
        try (InputStreamReader reader = new InputStreamReader(inputStream)) {
            JsonElement result = new Gson().fromJson(reader, JsonElement.class);
            return result != null;
        } catch (JsonSyntaxException | IllegalStateException | IOException e) {
            return false;
        }
    }
}
