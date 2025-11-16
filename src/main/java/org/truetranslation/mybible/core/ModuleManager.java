package org.truetranslation.mybible.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Manages MyBible module downloads, caching, installation, and updates.
 * Integrates with online registries to provide a comprehensive module management system.
 */
public class ModuleManager {

    private final Path configDir;
    private final Path sourcesDir;
    private final Path cacheDir;
    private final Path downloadCacheDir;
    private final Path registryCacheDir;
    private final Path cacheDbPath;
    private final Path configFilePath;
    private final Path etagCachePath;
    private final Path modulePath;

    private final Gson gson;
    private final int verbosity;
    private final ResourceBundle bundle;

    private static final Map<String, String> DEFAULT_SOURCES = new LinkedHashMap<>();
    static {
        DEFAULT_SOURCES.put("mybible.zone.registry", "https://mybible.zone/repository/registry/registry.zip");
        DEFAULT_SOURCES.put("myb.1gb.ru.registry", "http://myb.1gb.ru/registry.zip");
        DEFAULT_SOURCES.put("mybible.infoo.pro.registry", "http://mybible.infoo.pro/registry.zip");
        DEFAULT_SOURCES.put("mph4.ru.registry", "http://mph4.ru/registry.zip");
        DEFAULT_SOURCES.put("dropbox.registry", "https://dl.dropbox.com/s/keg0ptkkalux5fi/registry.zip");
        DEFAULT_SOURCES.put("mph4_test.registry", "http://mph4.ru/registry_test.zip");
        DEFAULT_SOURCES.put("myb.1gb.ru_test.registry", "http://myb.1gb.ru/registry_test.zip");
        DEFAULT_SOURCES.put("mybible.zone_test.registry", "https://mybible.zone/repository/registry/registry_test.zip");
    }

    private static final Set<String> MODULE_TYPES = new HashSet<>(Arrays.asList(
        "commentaries", "crossreferences", "devotions", "dictionary",
        "plan", "subheadings", "bundle"
    ));

    public ModuleManager(ConfigManager configManager, int verbosity) throws IOException {
        this.configDir = configManager.getDefaultConfigDir();
        this.sourcesDir = configDir.resolve("sources");
        this.cacheDir = configDir.resolve(".cache");
        this.downloadCacheDir = cacheDir.resolve("downloads");
        this.registryCacheDir = cacheDir.resolve("registries");
        this.cacheDbPath = configDir.resolve("cache.db");
        this.configFilePath = configDir.resolve("module_manager_config.json");
        this.etagCachePath = configDir.resolve("etags.json");

        String modulePathStr = configManager.getModulesPath();
        if (modulePathStr == null || modulePathStr.isEmpty()) {
            throw new IllegalStateException("Module path not configured. Use 'list --path <path>' first.");
        }
        this.modulePath = Paths.get(modulePathStr);

        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        this.verbosity = verbosity;

        ExternalResourceBundleLoader externalLoader = new ExternalResourceBundleLoader(configManager.getDefaultConfigDir());
        this.bundle = externalLoader.getBundle("i18n.messages");

        ensureDirectories();
        initializeCacheDatabase();
    }

    private void ensureDirectories() throws IOException {
        Files.createDirectories(configDir);
        Files.createDirectories(sourcesDir);
        Files.createDirectories(cacheDir);
        Files.createDirectories(downloadCacheDir);
        Files.createDirectories(registryCacheDir);
        Files.createDirectories(modulePath);

        try (Stream<Path> files = Files.list(sourcesDir)) {
            if (files.findAny().isEmpty()) {
                initializeSources(false);
            }
        }
    }

    public void initializeSources(boolean force) throws IOException {
        for (Map.Entry<String, String> entry : DEFAULT_SOURCES.entrySet()) {
            Path sourceFile = sourcesDir.resolve(entry.getKey());
            if (!Files.exists(sourceFile) || force) {
                Files.writeString(sourceFile, entry.getValue(), StandardCharsets.UTF_8);
            }
        }
        if (force && verbosity > 0) {
            System.out.println(bundle.getString("msg.sourcesReinitialized"));
        }
    }

    private void initializeCacheDatabase() throws IOException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + cacheDbPath)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS cached_modules (" +
                    "name TEXT NOT NULL, " +
                    "language TEXT, " +
                    "description TEXT NOT NULL, " +
                    "update_date TEXT NOT NULL, " +
                    "download_url TEXT NOT NULL, " +
                    "file_name TEXT NOT NULL, " +
                    "module_type TEXT NOT NULL, " +
                    "size TEXT, " +
                    "source_registry TEXT NOT NULL, " +
                    "PRIMARY KEY (name, update_date, download_url))");

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_module_name ON cached_modules(name)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_module_type ON cached_modules(module_type)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_language ON cached_modules(language)");
            }
        } catch (SQLException e) {
            throw new IOException(MessageFormat.format(bundle.getString("error.cacheDbInit"), e.getMessage()), e);
        }
    }

    private void initializeInstalledDatabase() throws IOException {
        Path installedDbPath = modulePath.resolve("mybible_installed.db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + installedDbPath)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS installed_modules (" +
                    "name TEXT PRIMARY KEY, " +
                    "language TEXT, " +
                    "description TEXT NOT NULL, " +
                    "update_date TEXT NOT NULL, " +
                    "install_date TEXT NOT NULL)");

                stmt.execute("CREATE TABLE IF NOT EXISTS installed_files (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "module_name TEXT NOT NULL, " +
                    "file_name TEXT NOT NULL, " +
                    "file_path TEXT NOT NULL, " +
                    "FOREIGN KEY (module_name) REFERENCES installed_modules(name) ON DELETE CASCADE)");

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_installed_module ON installed_files(module_name)");
            }
        } catch (SQLException e) {
            throw new IOException(MessageFormat.format(bundle.getString("error.installedDbInit"), e.getMessage()), e);
        }
    }

    public void updateCache(ProgressCallback progressCallback) throws IOException {
        Map<String, String> etagCache = loadEtagCache();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + cacheDbPath);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM cached_modules");
        } catch (SQLException e) {
            throw new IOException(MessageFormat.format(bundle.getString("error.cacheClear"), e.getMessage()), e);
        }

        List<Path> sourceFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.list(sourcesDir)) {
            sourceFiles = stream.filter(p -> p.getFileName().toString().endsWith(".registry") || p.getFileName().toString().endsWith(".extra")).collect(Collectors.toList());
        }

        AtomicInteger processed = new AtomicInteger(0);
        int total = sourceFiles.size();

        for (Path sourceFile : sourceFiles) {
            String fileName = sourceFile.getFileName().toString();
            if (progressCallback != null) {
                progressCallback.update(processed.get(), total, MessageFormat.format(bundle.getString("msg.processingSource"), fileName));
            }

            try {
                String url = Files.readString(sourceFile, StandardCharsets.UTF_8).trim();
                Path cachedRegPath = registryCacheDir.resolve(fileName);
                byte[] content = fetchWithEtag(url, cachedRegPath, etagCache);
                processRegistry(content, url, sourceFile.getFileName().toString().endsWith(".registry"));
            } catch (Exception e) {
                if (verbosity > 0) {
                    System.err.println(MessageFormat.format(bundle.getString("error.sourceProcessing"), fileName, e.getMessage()));
                }
            }
            processed.incrementAndGet();
        }

        saveEtagCache(etagCache);
        if (verbosity > 0) {
            System.out.println(bundle.getString("msg.cacheUpdateComplete"));
        }
    }

    private byte[] fetchWithEtag(String urlString, Path cachedPath, Map<String, String> etagCache) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "mybible-cli-java/1.5");

        String cachedEtag = etagCache.get(urlString);
        if (cachedEtag != null && Files.exists(cachedPath)) {
            conn.setRequestProperty("If-None-Match", cachedEtag);
        }

        conn.connect();
        int responseCode = conn.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
            return Files.readAllBytes(cachedPath);
        } else if (responseCode == HttpURLConnection.HTTP_OK) {
            byte[] content = conn.getInputStream().readAllBytes();
            Files.write(cachedPath, content);
            String etag = conn.getHeaderField("ETag");
            if (etag != null) {
                etagCache.put(urlString, etag);
            }
            return content;
        } else {
            throw new IOException(MessageFormat.format(bundle.getString("error.httpError"), responseCode, urlString));
        }
    }

    private void processRegistry(byte[] content, String registryUrl, boolean isZipped) throws IOException {
        List<CachedModule> modules = isZipped ? parseZippedRegistry(content, registryUrl) : parseExtraRegistry(content, registryUrl);
        if (!modules.isEmpty()) {
            insertModulesIntoCache(modules);
        }
    }

    private List<CachedModule> parseZippedRegistry(byte[] zipContent, String registryUrl) throws IOException {
        List<CachedModule> modules = new ArrayList<>();

        try (ByteArrayInputStream bais = new ByteArrayInputStream(zipContent);
             ZipInputStream zis = new ZipInputStream(bais)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".json")) {
                    String jsonContent = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    JsonObject root = JsonParser.parseString(jsonContent).getAsJsonObject();

                    Map<String, String> hosts = new HashMap<>();
                    if (root.has("hosts")) {
                        for (JsonElement hostElem : root.getAsJsonArray("hosts")) {
                            JsonObject hostObj = hostElem.getAsJsonObject();
                            hosts.put(hostObj.get("alias").getAsString(), hostObj.get("path").getAsString());
                        }
                    }

                    if (root.has("downloads")) {
                        for (JsonElement downloadElem : root.getAsJsonArray("downloads")) {
                            JsonObject mod = downloadElem.getAsJsonObject();

                            if (!mod.has("abr") || !mod.has("url")) continue;

                            String name = mod.get("abr").getAsString();
                            String fileName = mod.has("fil") ? mod.get("fil").getAsString() : name;

                            if (!mod.has("des") || !mod.has("upd")) continue;

                            for (JsonElement urlElem : mod.getAsJsonArray("url")) {
                                String urlTemplate = urlElem.getAsString();
                                int aliasStart = urlTemplate.indexOf("{") + 1;
                                int aliasEnd = urlTemplate.indexOf("}");

                                if (aliasStart == 0 || aliasEnd == -1) continue;

                                String alias = urlTemplate.substring(aliasStart, aliasEnd);
                                String filePart = urlTemplate.substring(aliasEnd + 1);

                                if (hosts.containsKey(alias)) {
                                    String downloadUrl = hosts.get(alias).replace("%s", filePart);
                                    String moduleType = extractModuleType(downloadUrl);

                                    modules.add(new CachedModule(
                                        name,
                                        mod.has("lng") ? mod.get("lng").getAsString() : null,
                                        mod.get("des").getAsString(),
                                        mod.get("upd").getAsString(),
                                        downloadUrl,
                                        fileName,
                                        moduleType,
                                        mod.has("siz") ? mod.get("siz").getAsString() : null,
                                        registryUrl
                                    ));
                                }
                            }
                        }
                    }
                    break;
                }
            }
        }
        return modules;
    }

    private List<CachedModule> parseExtraRegistry(byte[] jsonContent, String registryUrl) throws IOException {
        List<CachedModule> modules = new ArrayList<>();
        JsonObject root = JsonParser.parseString(new String(jsonContent, StandardCharsets.UTF_8)).getAsJsonObject();

        if (root.has("modules")) {
            for (JsonElement moduleElem : root.getAsJsonArray("modules")) {
                JsonObject mod = moduleElem.getAsJsonObject();

                if (!mod.has("download_url") || !mod.has("file_name") || !mod.has("description") || !mod.has("update_date")) {
                    continue;
                }

                String fileName = mod.get("file_name").getAsString();
                String moduleName = fileName.endsWith(".zip") ? fileName.substring(0, fileName.length() - 4) : fileName;
                String downloadUrl = mod.get("download_url").getAsString();
                String moduleType = extractModuleType(downloadUrl);

                modules.add(new CachedModule(
                    moduleName,
                    mod.has("language_code") ? mod.get("language_code").getAsString() : null,
                    mod.get("description").getAsString(),
                    mod.get("update_date").getAsString(),
                    downloadUrl,
                    fileName + ".zip",
                    moduleType,
                    null,
                    registryUrl
                ));
            }
        }
        return modules;
    }

    private String extractModuleType(String downloadUrl) {
        try {
            String urlFileName = downloadUrl.substring(downloadUrl.lastIndexOf('/') + 1);
            String decoded = URLDecoder.decode(urlFileName, StandardCharsets.UTF_8);
            String withoutZip = decoded.endsWith(".zip") ? decoded.substring(0, decoded.length() - 4) : decoded;
            String[] parts = withoutZip.split("\\.");
            if (parts.length > 1) {
                return parts[parts.length - 1];
            }
        } catch (Exception e) {
        }
        return "bible";
    }

    private void insertModulesIntoCache(List<CachedModule> modules) throws IOException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + cacheDbPath)) {
            String sql = "INSERT OR IGNORE INTO cached_modules " +
                "(name, language, description, update_date, download_url, file_name, module_type, size, source_registry) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                conn.setAutoCommit(false);

                for (CachedModule mod : modules) {
                    pstmt.setString(1, mod.name);
                    pstmt.setString(2, mod.language);
                    pstmt.setString(3, mod.description);
                    pstmt.setString(4, mod.updateDate);
                    pstmt.setString(5, mod.downloadUrl);
                    pstmt.setString(6, mod.fileName);
                    pstmt.setString(7, mod.moduleType);
                    pstmt.setString(8, mod.size);
                    pstmt.setString(9, mod.sourceRegistry);
                    pstmt.addBatch();
                }

                pstmt.executeBatch();
                conn.commit();
            }
        } catch (SQLException e) {
            throw new IOException(MessageFormat.format(bundle.getString("error.cacheInsert"), e.getMessage()), e);
        }
    }

    public List<CachedModule> listAvailableModules(String language, String moduleType, String nameFilter, String descFilter) throws IOException {
        List<CachedModule> modules = new ArrayList<>();

        StringBuilder sql = new StringBuilder(
            "SELECT name, language, description, update_date, download_url, file_name, module_type, size, source_registry " +
            "FROM cached_modules WHERE 1=1");

        List<String> params = new ArrayList<>();

        if (language != null && !language.isEmpty()) {
            sql.append(" AND LOWER(language) LIKE LOWER(?)");
            params.add("%" + language + "%");
        }
        if (moduleType != null && !moduleType.isEmpty()) {
            sql.append(" AND LOWER(module_type) LIKE LOWER(?)");
            params.add("%" + moduleType + "%");
        }
        if (nameFilter != null && !nameFilter.isEmpty()) {
            sql.append(" AND LOWER(name) LIKE LOWER(?)");
            params.add("%" + nameFilter + "%");
        }
        if (descFilter != null && !descFilter.isEmpty()) {
            sql.append(" AND LOWER(description) LIKE LOWER(?)");
            params.add("%" + descFilter + "%");
        }

        sql.append(" GROUP BY LOWER(name) ORDER BY LOWER(name)");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + cacheDbPath);
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                pstmt.setString(i + 1, params.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    modules.add(new CachedModule(
                        rs.getString("name"),
                        rs.getString("language"),
                        rs.getString("description"),
                        rs.getString("update_date"),
                        rs.getString("download_url"),
                        rs.getString("file_name"),
                        rs.getString("module_type"),
                        rs.getString("size"),
                        rs.getString("source_registry")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IOException(MessageFormat.format(bundle.getString("error.listAvailable"), e.getMessage()), e);
        }

        return modules;
    }

    public List<InstalledModule> listInstalledModules(String language, String moduleType, String nameFilter, String descFilter) throws IOException {
        List<InstalledModule> modules = new ArrayList<>();
        Path installedDbPath = modulePath.resolve("mybible_installed.db");

        if (!Files.exists(installedDbPath)) {
            return modules;
        }

        StringBuilder sql = new StringBuilder(
            "SELECT name, language, description, update_date, install_date FROM installed_modules WHERE 1=1");

        List<String> params = new ArrayList<>();

        if (language != null && !language.isEmpty()) {
            sql.append(" AND LOWER(language) LIKE LOWER(?)");
            params.add("%" + language + "%");
        }
        if (nameFilter != null && !nameFilter.isEmpty()) {
            sql.append(" AND LOWER(name) LIKE LOWER(?)");
            params.add("%" + nameFilter + "%");
        }
        if (descFilter != null && !descFilter.isEmpty()) {
            sql.append(" AND LOWER(description) LIKE LOWER(?)");
            params.add("%" + descFilter + "%");
        }

        sql.append(" ORDER BY LOWER(name)");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + installedDbPath);
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                pstmt.setString(i + 1, params.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String moduleName = rs.getString("name");

                    // Get module type from cache
                    String modType = "bible";
                    try {
                        CachedModule cached = getCachedModule(moduleName, null);
                        if (cached != null) {
                            modType = cached.moduleType;
                        }
                    } catch (IOException e) {
                        // If cache doesn't exist or module not in cache, use default
                    }

                    InstalledModule mod = new InstalledModule(
                        moduleName,
                        rs.getString("language"),
                        rs.getString("description"),
                        rs.getString("update_date"),
                        rs.getString("install_date")
                    );
                    mod.moduleType = modType;
                    mod.files = getInstalledFiles(conn, mod.name);
                    modules.add(mod);
                }
            }
        } catch (SQLException e) {
            throw new IOException(MessageFormat.format(bundle.getString("error.listInstalled"), e.getMessage()), e);
        }

        // Apply type filter if needed
        if (moduleType != null && !moduleType.isEmpty()) {
            modules = modules.stream().filter(mod -> 
                mod.moduleType.toLowerCase().contains(moduleType.toLowerCase())
            ).collect(Collectors.toList());
        }

        return modules;
    }

    private List<String> getInstalledFiles(Connection conn, String moduleName) throws SQLException {
        List<String> files = new ArrayList<>();
        String sql = "SELECT file_name FROM installed_files WHERE module_name = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, moduleName);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    files.add(rs.getString("file_name"));
                }
            }
        }
        return files;
    }

    public List<UpgradableModule> listUpgradableModules(String language, String moduleType, String nameFilter, String descFilter) throws IOException {
        List<UpgradableModule> upgradable = new ArrayList<>();
        List<InstalledModule> installed = listInstalledModules(language, moduleType, nameFilter, descFilter);

        for (InstalledModule inst : installed) {
            CachedModule latest = getCachedModule(inst.name, null);

            if (latest == null || latest.updateDate.compareTo(inst.updateDate) <= 0) {
                continue;
            }

            if (moduleType != null && !moduleType.isEmpty() && 
                !latest.moduleType.toLowerCase().contains(moduleType.toLowerCase())) {
                continue;
            }

            upgradable.add(new UpgradableModule(inst, latest));
        }

        return upgradable;
    }

    public ModuleInfo getModuleInfo(String name) throws IOException {
        InstalledModule installed = getInstalledModule(name);

        CachedModule cached = null;
        try {
            cached = getCachedModule(name, null);
        } catch (IOException e) {
            // Cache might not exist or be empty, continue without it
        }

        if (installed == null && cached == null) {
            if (!Files.exists(cacheDbPath)) {
                throw new IOException(MessageFormat.format(
                    bundle.getString("error.moduleNotFoundUpdateCache"), name));
            }
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + cacheDbPath);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as count FROM cached_modules")) {
                if (rs.next() && rs.getInt("count") == 0) {
                    throw new IOException(MessageFormat.format(
                        bundle.getString("error.moduleNotFoundUpdateCache"), name));
                }
            } catch (SQLException e) {
                // Ignore and fall through to generic error
            }

            throw new IOException(MessageFormat.format(bundle.getString("error.moduleNotFound"), name));
        }

        // If module is installed but not in cache, use installed info
        if (installed != null && cached == null) {
            cached = new CachedModule(
                installed.name,
                installed.language,
                installed.description,
                installed.updateDate,
                null,
                null,
                "installed",
                null,
                "local"
            );
        }
        return new ModuleInfo(cached, installed);
    }

    private CachedModule getCachedModule(String name, String version) throws IOException {
        StringBuilder sql = new StringBuilder(
            "SELECT name, language, description, update_date, download_url, file_name, module_type, size, source_registry " +
            "FROM cached_modules WHERE LOWER(name) LIKE LOWER(?)");

        if (version != null && !version.isEmpty()) {
            sql.append(" AND update_date = ?");
        }
        sql.append(" ORDER BY update_date DESC LIMIT 1");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + cacheDbPath);
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            pstmt.setString(1, name);
            if (version != null && !version.isEmpty()) {
                pstmt.setString(2, version);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new CachedModule(
                        rs.getString("name"),
                        rs.getString("language"),
                        rs.getString("description"),
                        rs.getString("update_date"),
                        rs.getString("download_url"),
                        rs.getString("file_name"),
                        rs.getString("module_type"),
                        rs.getString("size"),
                        rs.getString("source_registry")
                    );
                }
            }
        } catch (SQLException e) {
            throw new IOException(MessageFormat.format(bundle.getString("error.getCached"), e.getMessage()), e);
        }
        return null;
    }

    private InstalledModule getInstalledModule(String name) throws IOException {
        Path installedDbPath = modulePath.resolve("mybible_installed.db");
        if (!Files.exists(installedDbPath)) {
            return null;
        }

        String sql = "SELECT name, language, description, update_date, install_date " +
                    "FROM installed_modules WHERE LOWER(name) LIKE LOWER(?)";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + installedDbPath);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, name);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    InstalledModule mod = new InstalledModule(
                        rs.getString("name"),
                        rs.getString("language"),
                        rs.getString("description"),
                        rs.getString("update_date"),
                        rs.getString("install_date")
                    );
                    mod.files = getInstalledFiles(conn, mod.name);
                    return mod;
                }
            }
        } catch (SQLException e) {
            throw new IOException(MessageFormat.format(bundle.getString("error.getInstalled"), e.getMessage()), e);
        }
        return null;
    }

    public boolean installModule(String name, String version, boolean reinstall, ProgressCallback progressCallback) throws IOException {
        CachedModule mod = getCachedModule(name, version);
        if (mod == null) {
            throw new IOException(MessageFormat.format(bundle.getString("error.moduleNotFound"), name));
        }

        InstalledModule existing = getInstalledModule(name);
        if (existing != null) {
            if (reinstall) {
                if (verbosity > 0) {
                    System.out.println(MessageFormat.format(bundle.getString("msg.reinstalling"), name));
                }
                removeModule(name);
            } else {
                if (verbosity > 0) {
                    System.out.println(MessageFormat.format(bundle.getString("msg.alreadyInstalled"), name));
                }
                return false;
            }
        }

        String urlFileName = mod.downloadUrl.substring(mod.downloadUrl.lastIndexOf('/') + 1);
        String decodedFileName = URLDecoder.decode(urlFileName, StandardCharsets.UTF_8);
        if (!decodedFileName.endsWith(".zip")) {
            decodedFileName += ".zip";
        }
        Path zipPath = downloadCacheDir.resolve(decodedFileName);

        if (!Files.exists(zipPath)) {
            downloadFile(mod.downloadUrl, zipPath, progressCallback);
        }

        Map<String, Path> extractedFiles = extractModule(zipPath, mod.name, progressCallback);

        try {
            initializeInstalledDatabase();
        } catch (IOException e) {
            throw new IOException(MessageFormat.format(bundle.getString("error.installedDbInit"), e.getMessage()), e);
        }

        recordInstallation(mod, extractedFiles);

        if (verbosity > 0) {
            System.out.println(MessageFormat.format(bundle.getString("msg.installSuccess"), mod.name, mod.updateDate, extractedFiles.size()));
        }

        return true;
    }

    private void downloadFile(String urlString, Path destination, ProgressCallback progressCallback) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "mybible-cli-java/1.5");

        int contentLength = conn.getContentLength();

        try (InputStream in = conn.getInputStream();
             OutputStream out = Files.newOutputStream(destination)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            int totalRead = 0;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalRead += bytesRead;

                if (progressCallback != null && contentLength > 0) {
                    progressCallback.update(totalRead, contentLength, bundle.getString("msg.downloading"));
                }
            }
        }
    }

    private Map<String, Path> extractModule(Path zipPath, String moduleName, ProgressCallback progressCallback) throws IOException {
        Map<String, Path> extractedFiles = new LinkedHashMap<>();
        String cleanModuleName = moduleName.endsWith(".zip") ? moduleName.substring(0, moduleName.length() - 4) : moduleName;

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String originalFileName = entry.getName();
                String targetName = reconstructSqliteName(originalFileName, cleanModuleName);

                if (targetName.equals(originalFileName) && originalFileName.startsWith(".")) {
                    targetName = cleanModuleName + originalFileName;
                }

                Path targetPath = modulePath.resolve(targetName);
                Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                extractedFiles.put(targetName, targetPath);
                zis.closeEntry();
            }
        }
        return extractedFiles;
    }

    private String reconstructSqliteName(String originalFileName, String cleanModuleName) {
        if (!originalFileName.toLowerCase().endsWith(".sqlite3")) {
            return originalFileName;
        }

        String lowerFileName = originalFileName.toLowerCase();
        for (String moduleType : MODULE_TYPES) {
            if (lowerFileName.contains("." + moduleType.toLowerCase() + ".")) {
                return cleanModuleName + "." + moduleType + ".SQLite3";
            }
        }
        return cleanModuleName + ".SQLite3";
    }

    private void recordInstallation(CachedModule mod, Map<String, Path> files) throws IOException {
        Path installedDbPath = modulePath.resolve("mybible_installed.db");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + installedDbPath)) {
            conn.setAutoCommit(false);

            String modSql = "INSERT INTO installed_modules (name, language, description, update_date, install_date) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(modSql)) {
                pstmt.setString(1, mod.name);
                pstmt.setString(2, mod.language);
                pstmt.setString(3, mod.description);
                pstmt.setString(4, mod.updateDate);
                pstmt.setString(5, Instant.now().toString());
                pstmt.executeUpdate();
            }

            String fileSql = "INSERT INTO installed_files (module_name, file_name, file_path) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(fileSql)) {
                for (Map.Entry<String, Path> entry : files.entrySet()) {
                    pstmt.setString(1, mod.name);
                    pstmt.setString(2, entry.getKey());
                    pstmt.setString(3, entry.getValue().toString());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }

            conn.commit();
        } catch (SQLException e) {
            throw new IOException(MessageFormat.format(bundle.getString("error.recordInstall"), e.getMessage()), e);
        }
    }

    public boolean removeModule(String name) throws IOException {
        InstalledModule installed = getInstalledModule(name);

        if (installed == null) {
            if (verbosity > 0) {
                System.out.println(MessageFormat.format(bundle.getString("msg.notInstalled"), name));
            }
            return false;
        }

        for (String fileName : installed.files) {
            Path filePath = modulePath.resolve(fileName);
            if (Files.exists(filePath)) {
                try {
                    Files.delete(filePath);
                } catch (IOException e) {
                    if (verbosity > 0) {
                        System.err.println(MessageFormat.format(bundle.getString("error.deleteFile"), filePath));
                    }
                }
            }
        }

        Path installedDbPath = modulePath.resolve("mybible_installed.db");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + installedDbPath)) {
            conn.setAutoCommit(false);

            String filesSql = "DELETE FROM installed_files WHERE module_name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(filesSql)) {
                pstmt.setString(1, name);
                pstmt.executeUpdate();
            }

            String modSql = "DELETE FROM installed_modules WHERE name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(modSql)) {
                pstmt.setString(1, name);
                pstmt.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            throw new IOException(MessageFormat.format(bundle.getString("error.removeModule"), e.getMessage()), e);
        }

        if (verbosity > 0) {
            System.out.println(MessageFormat.format(bundle.getString("msg.moduleRemoved"), name));
        }

        return true;
    }

    public int upgradeModules(List<String> moduleNames, ProgressCallback progressCallback) throws IOException {
        List<String> toUpgrade = new ArrayList<>();

        if (moduleNames == null || moduleNames.isEmpty()) {
            List<UpgradableModule> upgradable = listUpgradableModules(null, null, null, null);
            toUpgrade = upgradable.stream().map(u -> u.installed.name).collect(Collectors.toList());
        } else {
            for (String name : moduleNames) {
                InstalledModule inst = getInstalledModule(name);
                if (inst == null) {
                    if (verbosity > 0) {
                        System.out.println(MessageFormat.format(bundle.getString("msg.notInstalled"), name));
                    }
                    continue;
                }

                CachedModule latest = getCachedModule(name, null);
                if (latest != null && latest.updateDate.compareTo(inst.updateDate) > 0) {
                    toUpgrade.add(name);
                }
            }
        }

        if (toUpgrade.isEmpty()) {
            if (verbosity > 0) {
                System.out.println(bundle.getString("msg.allUpToDate"));
            }
            return 0;
        }

        int upgraded = 0;
        for (String name : toUpgrade) {
            if (verbosity > 0) {
                System.out.println(MessageFormat.format(bundle.getString("msg.upgrading"), name));
            }

            if (removeModule(name) && installModule(name, null, false, progressCallback)) {
                upgraded++;
            }
        }

        return upgraded;
    }

    public List<String> listModuleVersions(String name) throws IOException {
        List<String> versions = new ArrayList<>();
        String sql = "SELECT DISTINCT update_date FROM cached_modules WHERE LOWER(name) LIKE LOWER(?) ORDER BY update_date DESC";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + cacheDbPath);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, name);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    versions.add(rs.getString("update_date"));
                }
            }
        } catch (SQLException e) {
            throw new IOException(MessageFormat.format(bundle.getString("error.listVersions"), e.getMessage()), e);
        }

        return versions;
    }

    public void purgeCache(boolean full) throws IOException {
        if (full) {
            deleteDirectory(configDir);
            if (verbosity > 0) {
                System.out.println(bundle.getString("msg.configPurged"));
            }
        } else {
            deleteDirectory(cacheDir);
            deleteDirectory(registryCacheDir);
            Files.deleteIfExists(etagCachePath);
            if (verbosity > 0) {
                System.out.println(bundle.getString("msg.cachePurged"));
            }
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted((a, b) -> -a.compareTo(b)).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                    }
                });
            }
        }
    }

    private Map<String, String> loadEtagCache() throws IOException {
        if (!Files.exists(etagCachePath)) {
            return new HashMap<>();
        }
        try {
            String json = Files.readString(etagCachePath, StandardCharsets.UTF_8);
            return gson.fromJson(json, new com.google.gson.reflect.TypeToken<Map<String, String>>(){}.getType());
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private void saveEtagCache(Map<String, String> cache) throws IOException {
        Files.writeString(etagCachePath, gson.toJson(cache), StandardCharsets.UTF_8);
    }

    public static class CachedModule {
        public final String name;
        public final String language;
        public final String description;
        public final String updateDate;
        public final String downloadUrl;
        public final String fileName;
        public final String moduleType;
        public final String size;
        public final String sourceRegistry;

        public CachedModule(String name, String language, String description, String updateDate,
                           String downloadUrl, String fileName, String moduleType, String size,
                           String sourceRegistry) {
            this.name = name;
            this.language = language;
            this.description = description;
            this.updateDate = updateDate;
            this.downloadUrl = downloadUrl;
            this.fileName = fileName;
            this.moduleType = moduleType;
            this.size = size;
            this.sourceRegistry = sourceRegistry;
        }
    }

    public static class InstalledModule {
        public final String name;
        public final String language;
        public final String description;
        public final String updateDate;
        public final String installDate;
        public String moduleType;
        public List<String> files;

        public InstalledModule(String name, String language, String description, String updateDate, String installDate) {
            this.name = name;
            this.language = language;
            this.description = description;
            this.updateDate = updateDate;
            this.installDate = installDate;
            this.files = new ArrayList<>();
        }
    }

    public static class UpgradableModule {
        public final InstalledModule installed;
        public final CachedModule latest;

        public UpgradableModule(InstalledModule installed, CachedModule latest) {
            this.installed = installed;
            this.latest = latest;
        }
    }

    /**
     * Install a module from a local SQL or ZIP file.
     * 
     * @param filePath Path to the local .sql, .sqlite3, or .zip file
     * @return true if installation was successful
     * @throws IOException if installation fails
     */
    public boolean installFromFile(String filePath) throws IOException {
        Path sourceFile = Paths.get(filePath);
        
        if (!Files.exists(sourceFile)) {
            throw new IOException(MessageFormat.format(
                bundle.getString("error.fileNotFound"), filePath));
        }
        
        String fileName = sourceFile.getFileName().toString();
        String fileExtension = getFileExtension(fileName).toLowerCase();
        
        // Validate file type
        if (!fileExtension.equals("zip") && !fileExtension.equals("sql") && 
            !fileExtension.equals("sqlite3")) {
            throw new IOException(MessageFormat.format(
                bundle.getString("error.invalidFileType"), fileName));
        }
        
        // Determine module name and type
        ModuleFileInfo moduleInfo = parseModuleFileInfo(fileName, sourceFile);
        
        // Check if bundle type
        if ("bundle".equals(moduleInfo.moduleType)) {
            throw new IOException(bundle.getString("error.bundleNotSupported"));
        }
        
        // Check if module already registered in database
        InstalledModule existing = getInstalledModule(moduleInfo.moduleName);
        if (existing != null) {
            throw new IOException(MessageFormat.format(
                bundle.getString("error.moduleAlreadyInstalled"), moduleInfo.moduleName));
        }
        
        // Initialize installed database
        try {
            initializeInstalledDatabase();
        } catch (IOException e) {
            throw new IOException(MessageFormat.format(
                bundle.getString("error.installedDbInit"), e.getMessage()), e);
        }
        
        Map<String, Path> installedFiles = new LinkedHashMap<>();
        
        if (fileExtension.equals("zip")) {
            // Handle ZIP file
            installedFiles = installFromZipFile(sourceFile, moduleInfo);
        } else {
            // Handle SQL/SQLite3 file
            installedFiles = installFromSqlFile(sourceFile, moduleInfo);
        }
        
        // Record installation in database
        recordFileInstallation(moduleInfo, installedFiles);
        
        if (verbosity > 0) {
            System.out.println(MessageFormat.format(
                bundle.getString("msg.fileInstallSuccess"), 
                moduleInfo.moduleName, installedFiles.size()));
        }
        
        return true;
    }
    
    /**
     * Parse module information from file name and optionally from SQL info table.
     */
    private ModuleFileInfo parseModuleFileInfo(String fileName, Path sourceFile) throws IOException {
        String baseName = fileName;
        String extension = getFileExtension(fileName).toLowerCase();
        
        // Remove extension
        if (extension.equals("zip")) {
            baseName = fileName.substring(0, fileName.length() - 4);
        } else if (extension.equals("sqlite3")) {
            baseName = fileName.substring(0, fileName.length() - 8);
        } else if (extension.equals("sql")) {
            baseName = fileName.substring(0, fileName.length() - 4);
        }
        
        // Determine module type from suffix
        String moduleType = "bible";
        String moduleName = baseName;
        
        for (String type : MODULE_TYPES) {
            if (baseName.toLowerCase().endsWith("." + type.toLowerCase())) {
                moduleType = type;
                moduleName = baseName.substring(0, 
                    baseName.length() - type.length() - 1);
                break;
            }
        }
        
        // For ZIP files, module name is the zip file name (without .zip)
        if (extension.equals("zip")) {
            moduleName = fileName.substring(0, fileName.length() - 4);
        }
        
        // Extract language and description from SQL info table if possible
        String language = null;
        String description = moduleName;
        
        if (!extension.equals("zip")) {
            try {
                ModuleSqlInfo sqlInfo = extractInfoFromSqlFile(sourceFile);
                if (sqlInfo != null) {
                    language = sqlInfo.language;
                    description = sqlInfo.description != null ? 
                        sqlInfo.description : moduleName;
                }
            } catch (Exception e) {
                if (verbosity > 1) {
                    System.err.println(MessageFormat.format(
                        bundle.getString("warning.couldNotReadInfo"), 
                        fileName, e.getMessage()));
                }
            }
        }
        
        return new ModuleFileInfo(moduleName, moduleType, language, description);
    }
    
    /**
     * Extract language and description from SQL file's info table.
     */
    private ModuleSqlInfo extractInfoFromSqlFile(Path sqlFile) throws IOException {
        String dbUrl = "jdbc:sqlite:" + sqlFile.toAbsolutePath();
        
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            // Check if info table exists
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='info'")) {
                if (!rs.next()) {
                    return null;
                }
            }
            
            String language = null;
            String description = null;
            
            // Read from info table
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name, value FROM info")) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    String value = rs.getString("value");
                    
                    if ("language".equalsIgnoreCase(name)) {
                        language = value;
                    } else if ("description".equalsIgnoreCase(name)) {
                        description = value;
                    }
                }
            }
            
            return new ModuleSqlInfo(language, description);
        } catch (SQLException e) {
            throw new IOException("Failed to read SQL file info: " + e.getMessage(), e);
        }
    }
    
    /**
     * Install module from a SQL/SQLite3 file.
     */
    private Map<String, Path> installFromSqlFile(Path sourceFile, ModuleFileInfo moduleInfo) 
            throws IOException {
        Map<String, Path> installedFiles = new LinkedHashMap<>();
        
        // Construct target file name
        String targetFileName;
        if ("bible".equals(moduleInfo.moduleType)) {
            targetFileName = moduleInfo.moduleName + ".SQLite3";
        } else {
            targetFileName = moduleInfo.moduleName + "." + 
                moduleInfo.moduleType + ".SQLite3";
        }
        
        Path targetPath = modulePath.resolve(targetFileName);
        
        // Check if file already exists in module folder
        if (Files.exists(targetPath)) {
            // Calculate MD5 of both files
            String sourceMd5 = calculateMD5(sourceFile);
            String targetMd5 = calculateMD5(targetPath);
            
            if (sourceMd5.equals(targetMd5)) {
                // Files are identical, just add to database
                if (verbosity > 0) {
                    System.out.println(MessageFormat.format(
                        bundle.getString("msg.fileAlreadyExistsIdentical"), 
                        targetFileName));
                }
            } else {
                // Files are different, refuse installation
                throw new IOException(MessageFormat.format(
                    bundle.getString("error.fileDifferentExists"), 
                    targetFileName));
            }
        } else {
            // Copy file to module directory
            Files.copy(sourceFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
        
        installedFiles.put(targetFileName, targetPath);
        return installedFiles;
    }
    
    /**
     * Install module from a ZIP file.
     */
    private Map<String, Path> installFromZipFile(Path zipFile, ModuleFileInfo moduleInfo) 
            throws IOException {
        Map<String, Path> installedFiles = new LinkedHashMap<>();
        
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                
                String originalFileName = entry.getName();
                
                // Determine target file name based on module name and type
                String targetFileName = determineTargetFileName(
                    originalFileName, moduleInfo.moduleName, moduleInfo.moduleType);
                
                Path targetPath = modulePath.resolve(targetFileName);
                
                // Check if file already exists
                if (Files.exists(targetPath)) {
                    // Calculate MD5 of zip entry and existing file
                    byte[] zipContent = zis.readAllBytes();
                    String zipMd5 = calculateMD5(zipContent);
                    String existingMd5 = calculateMD5(targetPath);
                    
                    if (zipMd5.equals(existingMd5)) {
                        if (verbosity > 0) {
                            System.out.println(MessageFormat.format(
                                bundle.getString("msg.fileAlreadyExistsIdentical"), 
                                targetFileName));
                        }
                    } else {
                        throw new IOException(MessageFormat.format(
                            bundle.getString("error.fileDifferentExists"), 
                            targetFileName));
                    }
                } else {
                    // Need to re-read the entry since we consumed it for MD5
                    Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                
                installedFiles.put(targetFileName, targetPath);
                zis.closeEntry();
            }
        }
        
        // Extract info from first SQLite file if available
        if (moduleInfo.language == null || moduleInfo.description == null) {
            for (Path installedFile : installedFiles.values()) {
                try {
                    ModuleSqlInfo sqlInfo = extractInfoFromSqlFile(installedFile);
                    if (sqlInfo != null) {
                        if (moduleInfo.language == null) {
                            moduleInfo.language = sqlInfo.language;
                        }
                        if (moduleInfo.description == null || 
                            moduleInfo.description.equals(moduleInfo.moduleName)) {
                            moduleInfo.description = sqlInfo.description != null ? 
                                sqlInfo.description : moduleInfo.moduleName;
                        }
                        break;
                    }
                } catch (Exception e) {
                    // Continue to next file
                }
            }
        }
        
        return installedFiles;
    }
    
    /**
     * Determine target file name for a file from ZIP archive.
     */
    private String determineTargetFileName(String originalFileName, 
                                           String moduleName, String moduleType) {
        String lowerOriginal = originalFileName.toLowerCase();
        
        if (!lowerOriginal.endsWith(".sqlite3")) {
            return originalFileName;
        }
        
        // Check if it's a module type file
        for (String type : MODULE_TYPES) {
            if (lowerOriginal.contains("." + type.toLowerCase() + ".")) {
                return moduleName + "." + type + ".SQLite3";
            }
        }
        
        // Default bible module
        return moduleName + ".SQLite3";
    }
    
    /**
     * Calculate MD5 checksum of a file.
     */
    private String calculateMD5(Path filePath) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream is = Files.newInputStream(filePath);
                 DigestInputStream dis = new DigestInputStream(is, md)) {
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // Reading file to update digest
                }
            }
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("MD5 algorithm not available", e);
        }
    }
    
    /**
     * Calculate MD5 checksum of byte array.
     */
    private String calculateMD5(byte[] data) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(data);
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("MD5 algorithm not available", e);
        }
    }
    
    /**
     * Convert byte array to hex string.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * Record installation of a module from file.
     */
    private void recordFileInstallation(ModuleFileInfo moduleInfo, 
                                        Map<String, Path> files) throws IOException {
        Path installedDbPath = modulePath.resolve("mybible_installed.db");
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + installedDbPath)) {
            conn.setAutoCommit(false);
            
            String modSql = "INSERT INTO installed_modules " +
                "(name, language, description, update_date, install_date) VALUES (?, ?, ?, ?, ?)";
            
            try (PreparedStatement pstmt = conn.prepareStatement(modSql)) {
                pstmt.setString(1, moduleInfo.moduleName);
                pstmt.setString(2, moduleInfo.language);
                pstmt.setString(3, moduleInfo.description);
                pstmt.setString(4, "0.0.1");  // Version for file installations
                pstmt.setString(5, Instant.now().toString());
                pstmt.executeUpdate();
            }
            
            String fileSql = "INSERT INTO installed_files " +
                "(module_name, file_name, file_path) VALUES (?, ?, ?)";
            
            try (PreparedStatement pstmt = conn.prepareStatement(fileSql)) {
                for (Map.Entry<String, Path> entry : files.entrySet()) {
                    pstmt.setString(1, moduleInfo.moduleName);
                    pstmt.setString(2, entry.getKey());
                    pstmt.setString(3, entry.getValue().toString());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
            
            conn.commit();
        } catch (SQLException e) {
            throw new IOException(MessageFormat.format(
                bundle.getString("error.recordInstall"), e.getMessage()), e);
        }
    }
    
    /**
     * Get file extension from file name.
     */
    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1);
        }
        return "";
    }
    
    // Helper classes
    private static class ModuleFileInfo {
        String moduleName;
        String moduleType;
        String language;
        String description;
        
        ModuleFileInfo(String moduleName, String moduleType, 
                       String language, String description) {
            this.moduleName = moduleName;
            this.moduleType = moduleType;
            this.language = language;
            this.description = description;
        }
    }
    
    private static class ModuleSqlInfo {
        String language;
        String description;
        
        ModuleSqlInfo(String language, String description) {
            this.language = language;
            this.description = description;
        }
    }

    public static class ModuleInfo {
        public final CachedModule cached;
        public final InstalledModule installed;
        public final boolean isInstalled;
        public final boolean hasUpdate;

        public ModuleInfo(CachedModule cached, InstalledModule installed) {
            this.cached = cached;
            this.installed = installed;
            this.isInstalled = installed != null;
            this.hasUpdate = installed != null && cached != null && cached.updateDate.compareTo(installed.updateDate) > 0;
        }
    }

    @FunctionalInterface
    public interface ProgressCallback {
        void update(int current, int total, String message);
    }
}
