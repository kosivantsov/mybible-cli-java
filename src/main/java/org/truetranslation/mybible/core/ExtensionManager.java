package org.truetranslation.mybible.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ExtensionManager {

    private final ConfigManager configManager;
    private final Path extensionsDir;
    private final Path resourcesDir;
    private final Path i18nDir;
    private final Path guiThemesDir;
    private final Path sourcesDir;
    private final Path cacheDir;
    private final ResourceBundle bundle;
    private final int verbosity;

    private static final String MANIFEST_FILENAME = "manifest.json";
    private static final Set<String> PROTECTED_FILES = Set.of("default_mapping.json");
    private static final String DEFAULT_REGISTRY_URL = "https://raw.githubusercontent.com/kosivantsov/mybible-cli-extensions/main/registry.json";
    private static final String DEFAULT_REGISTRY_NAME = "kosivantsov.extregistry";

    private static final Map<String, Set<String>> ALLOWED_FILE_TYPES = Map.of(
        "theme", Set.of("themes"),
        "mapping", Set.of("mappings"),
        "localization", Set.of("resources"),
        "bundle", Set.of("mappings", "resources", "themes")
    );

    public ExtensionManager(ConfigManager configManager, int verbosity) throws IOException {
        this.configManager = configManager;
        this.verbosity = verbosity;

        Path configDir = configManager.getDefaultConfigDir();
        this.extensionsDir = configDir.resolve("ext");
        this.resourcesDir = configDir.resolve("resources");
        this.i18nDir = resourcesDir.resolve("i18n");
        this.guiThemesDir = configDir.resolve("gui_themes");
        this.sourcesDir = configDir.resolve("sources");
        this.cacheDir = configDir.resolve(".cache").resolve("extensions");

        ExternalResourceBundleLoader loader = new ExternalResourceBundleLoader(configDir);
        this.bundle = loader.getBundle("i18n.messages");

        initializeDefaultRegistry();
    }

    private void initializeDefaultRegistry() throws IOException {
        Files.createDirectories(sourcesDir);
        Path defaultRegistry = sourcesDir.resolve(DEFAULT_REGISTRY_NAME);
        
        if (!Files.exists(defaultRegistry)) {
            Files.writeString(defaultRegistry, DEFAULT_REGISTRY_URL, StandardCharsets.UTF_8);
            if (verbosity > 0) {
                System.out.println(bundle.getString("msg.extensionmgr.registryInitialized"));
            }
        }
    }

    public void updateRegistries() throws IOException {
        Files.createDirectories(cacheDir);
        
        if (!Files.exists(sourcesDir)) {
            throw new IOException(bundle.getString("error.extensionmgr.noSources"));
        }

        try (Stream<Path> files = Files.list(sourcesDir)) {
            List<Path> registries = files
                .filter(p -> p.toString().endsWith(".extregistry"))
                .collect(Collectors.toList());

            for (Path registryFile : registries) {
                String url = Files.readString(registryFile, StandardCharsets.UTF_8).trim();
                String registryName = registryFile.getFileName().toString().replace(".extregistry", "");
                
                if (verbosity > 0) {
                    System.out.println(MessageFormat.format(
                        bundle.getString("msg.extensionmgr.updatingRegistry"), registryName));
                }
                
                downloadRegistry(url, registryName);
            }
        }

        if (verbosity > 0) {
            System.out.println(bundle.getString("msg.extensionmgr.registryUpdateComplete"));
        }
    }

    private void downloadRegistry(String urlString, String registryName) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        try (InputStream in = conn.getInputStream()) {
            Path cachePath = cacheDir.resolve(registryName + ".json");
            Files.copy(in, cachePath, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            conn.disconnect();
        }
    }

    public List<RegistryExtension> listAvailableExtensions(String typeFilter, String nameFilter) throws IOException {
        List<RegistryExtension> allExtensions = new ArrayList<>();
        
        if (!Files.exists(cacheDir)) {
            throw new IOException(bundle.getString("error.extensionmgr.noCachedRegistries"));
        }

        try (Stream<Path> files = Files.list(cacheDir)) {
            List<Path> cacheFiles = files
                .filter(p -> p.toString().endsWith(".json"))
                .collect(Collectors.toList());

            for (Path cacheFile : cacheFiles) {
                try {
                    String json = Files.readString(cacheFile, StandardCharsets.UTF_8);
                    ExtensionRegistry registry = new Gson().fromJson(json, ExtensionRegistry.class);
                    
                    if (registry.extensions != null) {
                        allExtensions.addAll(registry.extensions);
                    }
                } catch (Exception e) {
                    if (verbosity > 0) {
                        System.err.println(MessageFormat.format(
                            bundle.getString("error.extensionmgr.registryReadFailed"), e.getMessage()));
                    }
                }
            }
        }

        Stream<RegistryExtension> stream = allExtensions.stream();
        
        if (typeFilter != null && !typeFilter.isEmpty()) {
            stream = stream.filter(ext -> ext.type != null && ext.type.equalsIgnoreCase(typeFilter));
        }
        
        if (nameFilter != null && !nameFilter.isEmpty()) {
            String lowerFilter = nameFilter.toLowerCase();
            stream = stream.filter(ext -> 
                (ext.name != null && ext.name.toLowerCase().contains(lowerFilter)) ||
                (ext.description != null && ext.description.toLowerCase().contains(lowerFilter))
            );
        }

        return stream.collect(Collectors.toList());
    }

    public List<ExtensionInfo> listInstalledExtensions() throws IOException {
        if (!Files.exists(extensionsDir)) {
            return new ArrayList<>();
        }

        List<ExtensionInfo> extensions = new ArrayList<>();

        try (Stream<Path> files = Files.list(extensionsDir)) {
            List<Path> manifestFiles = files
                .filter(p -> p.toString().endsWith(".json"))
                .collect(Collectors.toList());

            for (Path manifestPath : manifestFiles) {
                try {
                    ExtensionManifest manifest = loadManifest(manifestPath);
                    extensions.add(new ExtensionInfo(manifest, manifestPath));
                } catch (Exception e) {
                    if (verbosity > 0) {
                        System.err.println(MessageFormat.format(
                            bundle.getString("error.extensionmgr.manifestLoadFailed"),
                            manifestPath.getFileName().toString(),
                            e.getMessage()
                        ));
                    }
                }
            }
        }

        return extensions;
    }

    public List<ExtensionInfo> listUpgradableExtensions() throws IOException {
        List<ExtensionInfo> installed = listInstalledExtensions();
        List<RegistryExtension> available = listAvailableExtensions(null, null);
        List<ExtensionInfo> upgradable = new ArrayList<>();

        Map<String, RegistryExtension> availableMap = available.stream()
            .collect(Collectors.toMap(ext -> ext.name, ext -> ext, (a, b) -> a));

        for (ExtensionInfo installedExt : installed) {
            RegistryExtension availableExt = availableMap.get(installedExt.manifest.name);
            if (availableExt != null) {
                int comparison = compareVersions(installedExt.manifest.version, availableExt.version);
                if (comparison < 0) {
                    upgradable.add(installedExt);
                }
            }
        }

        return upgradable;
    }

    public void upgradeExtension(String extensionName) throws IOException, ExtensionValidationException {
        List<ExtensionInfo> installed = listInstalledExtensions();
        ExtensionInfo installedExt = installed.stream()
            .filter(ext -> ext.manifest.name.equals(extensionName))
            .findFirst()
            .orElseThrow(() -> new IOException(MessageFormat.format(
                bundle.getString("error.extensionmgr.notInstalled"), extensionName)));

        List<RegistryExtension> available = listAvailableExtensions(null, null);
        RegistryExtension availableExt = available.stream()
            .filter(ext -> ext.name.equals(extensionName))
            .findFirst()
            .orElseThrow(() -> new IOException(MessageFormat.format(
                bundle.getString("error.extensionmgr.notInRegistry"), extensionName)));

        int comparison = compareVersions(installedExt.manifest.version, availableExt.version);
        if (comparison >= 0) {
            if (verbosity > 0) {
                System.out.println(MessageFormat.format(
                    bundle.getString("msg.extensionmgr.alreadyLatest"), extensionName));
            }
            return;
        }

        if (verbosity > 0) {
            System.out.println(MessageFormat.format(
                bundle.getString("msg.extensionmgr.upgrading"),
                extensionName,
                installedExt.manifest.version,
                availableExt.version
            ));
        }

        uninstallExtension(extensionName);
        installExtensionFromRegistry(extensionName);

        if (verbosity > 0) {
            System.out.println(MessageFormat.format(
                bundle.getString("msg.extensionmgr.upgraded"),
                extensionName,
                availableExt.version
            ));
        }
    }

    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        int maxLength = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < maxLength; i++) {
            int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;

            if (num1 < num2) return -1;
            if (num1 > num2) return 1;
        }
        return 0;
    }

    public void installExtension(String extensionNameOrPath) throws IOException, ExtensionValidationException {
        Path zipPath = Path.of(extensionNameOrPath);
        
        if (Files.exists(zipPath)) {
            installExtensionFromFile(zipPath);
        } else {
            installExtensionFromRegistry(extensionNameOrPath);
        }
    }

    private void installExtensionFromRegistry(String extensionName) throws IOException, ExtensionValidationException {
        List<RegistryExtension> available = listAvailableExtensions(null, null);
        RegistryExtension target = available.stream()
            .filter(ext -> ext.name.equals(extensionName))
            .findFirst()
            .orElseThrow(() -> new IOException(MessageFormat.format(
                bundle.getString("error.extensionmgr.notFoundInRegistry"), extensionName)));

        if (verbosity > 0) {
            System.out.println(MessageFormat.format(
                bundle.getString("msg.extensionmgr.downloading"),
                target.name,
                target.version
            ));
        }

        Path tempZip = downloadExtension(target.downloadUrl);
        
        try {
            if (target.sha256 != null) {
                verifyChecksum(tempZip, target.sha256);
            }
            
            installExtensionFromFile(tempZip);
        } finally {
            Files.deleteIfExists(tempZip);
        }
    }

    private Path downloadExtension(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        Path tempFile = Files.createTempFile("extension-", ".zip");
        
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            conn.disconnect();
        }

        return tempFile;
    }

    private void verifyChecksum(Path file, String expectedSha256) throws IOException, ExtensionValidationException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(file);
            byte[] hashBytes = digest.digest(fileBytes);
            
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            String actualSha256 = sb.toString();
            
            if (!actualSha256.equals(expectedSha256)) {
                throw new ExtensionValidationException(
                    bundle.getString("error.extensionmgr.checksumFailed")
                );
            }
            
            if (verbosity > 0) {
                System.out.println(bundle.getString("msg.extensionmgr.checksumVerified"));
            }
        } catch (Exception e) {
            if (e instanceof ExtensionValidationException) {
                throw (ExtensionValidationException) e;
            }
            throw new IOException(MessageFormat.format(
                bundle.getString("error.extensionmgr.checksumError"), e.getMessage()), e);
        }
    }

    private void installExtensionFromFile(Path zipPath) throws IOException, ExtensionValidationException {
        if (!Files.exists(zipPath)) {
            throw new IOException(MessageFormat.format(
                bundle.getString("error.extensionmgr.fileNotFound"),
                zipPath.toString()
            ));
        }

        if (!zipPath.toString().toLowerCase().endsWith(".zip")) {
            throw new ExtensionValidationException(bundle.getString("error.extensionmgr.mustBeZip"));
        }

        ExtensionManifest manifest;
        Map<String, byte[]> fileContents = new HashMap<>();

        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            manifest = validateExtension(zipFile, fileContents);
        }

        checkFileConflicts(manifest);

        Files.createDirectories(extensionsDir);
        Files.createDirectories(i18nDir);
        Files.createDirectories(guiThemesDir);

        installFiles(manifest, fileContents);
        saveManifest(manifest);

        if (verbosity > 0) {
            System.out.println(MessageFormat.format(
                bundle.getString("msg.extensionmgr.installed"),
                manifest.name,
                manifest.version
            ));
        }
    }

    public void uninstallExtension(String extensionName) throws IOException {
        if (!Files.exists(extensionsDir)) {
            throw new IOException(bundle.getString("error.extensionmgr.noneInstalled"));
        }

        Path manifestPath = extensionsDir.resolve(extensionName + ".json");
        if (!Files.exists(manifestPath)) {
            throw new IOException(MessageFormat.format(
                bundle.getString("error.extensionmgr.notInstalled"), extensionName));
        }

        ExtensionManifest manifest = loadManifest(manifestPath);
        Path configDir = configManager.getDefaultConfigDir();

        if (manifest.files != null) {
            if (manifest.files.mappings != null) {
                for (String mappingFile : manifest.files.mappings) {
                    Path mappingPath = configDir.resolve(mappingFile);
                    Files.deleteIfExists(mappingPath);
                    if (verbosity > 0) {
                        System.out.println(MessageFormat.format(
                            bundle.getString("msg.extensionmgr.deleted"), mappingFile));
                    }
                }
            }

            if (manifest.files.resources != null) {
                for (String resourceFile : manifest.files.resources) {
                    Path resourcePath = i18nDir.resolve(resourceFile);
                    Files.deleteIfExists(resourcePath);
                    if (verbosity > 0) {
                        System.out.println(MessageFormat.format(
                            bundle.getString("msg.extensionmgr.deleted"), resourceFile));
                    }
                }
            }

            if (manifest.files.themes != null) {
                for (String themeFile : manifest.files.themes) {
                    Path themePath = guiThemesDir.resolve(themeFile);
                    Files.deleteIfExists(themePath);
                    if (verbosity > 0) {
                        System.out.println(MessageFormat.format(
                            bundle.getString("msg.extensionmgr.deleted"), themeFile));
                    }
                }
            }
        }

        Files.delete(manifestPath);

        if (verbosity > 0) {
            System.out.println(MessageFormat.format(
                bundle.getString("msg.extensionmgr.uninstalled"), manifest.name));
        }
    }

    private void checkFileConflicts(ExtensionManifest manifest) 
            throws IOException, ExtensionValidationException {
        List<String> conflicts = new ArrayList<>();
        Path configDir = configManager.getDefaultConfigDir();

        if (manifest.files == null) {
            return;
        }

        if (manifest.files.mappings != null) {
            for (String mappingFile : manifest.files.mappings) {
                if (PROTECTED_FILES.contains(mappingFile)) {
                    conflicts.add(MessageFormat.format(
                        bundle.getString("error.extensionmgr.protectedFile"),
                        mappingFile
                    ));
                    continue;
                }

                Path targetPath = configDir.resolve(mappingFile);
                if (Files.exists(targetPath)) {
                    String owner = findFileOwner(mappingFile, FileType.MAPPING);
                    if (owner != null && !owner.equals(manifest.name)) {
                        conflicts.add(MessageFormat.format(
                            bundle.getString("error.extensionmgr.fileConflict"),
                            mappingFile,
                            owner
                        ));
                    }
                }
            }
        }

        if (manifest.files.resources != null) {
            for (String resourceFile : manifest.files.resources) {
                Path targetPath = i18nDir.resolve(resourceFile);
                if (Files.exists(targetPath)) {
                    String owner = findFileOwner(resourceFile, FileType.RESOURCE);
                    if (owner != null && !owner.equals(manifest.name)) {
                        conflicts.add(MessageFormat.format(
                            bundle.getString("error.extensionmgr.fileConflict"),
                            resourceFile,
                            owner
                        ));
                    }
                }
            }
        }

        if (manifest.files.themes != null) {
            for (String themeFile : manifest.files.themes) {
                Path targetPath = guiThemesDir.resolve(themeFile);
                if (Files.exists(targetPath)) {
                    String owner = findFileOwner(themeFile, FileType.THEME);
                    if (owner != null && !owner.equals(manifest.name)) {
                        conflicts.add(MessageFormat.format(
                            bundle.getString("error.extensionmgr.fileConflict"),
                            themeFile,
                            owner
                        ));
                    }
                }
            }
        }

        if (!conflicts.isEmpty()) {
            throw new ExtensionValidationException(
                bundle.getString("error.extensionmgr.conflictsDetected") + "\n" + String.join("\n", conflicts)
            );
        }
    }

    private String findFileOwner(String filename, FileType fileType) throws IOException {
        if (!Files.exists(extensionsDir)) {
            return null;
        }

        try (Stream<Path> files = Files.list(extensionsDir)) {
            List<Path> manifestFiles = files
                .filter(p -> p.toString().endsWith(".json"))
                .collect(Collectors.toList());

            for (Path manifestPath : manifestFiles) {
                try {
                    ExtensionManifest manifest = loadManifest(manifestPath);
                    if (manifest.files == null) continue;

                    List<String> filesToCheck = null;
                    switch (fileType) {
                        case MAPPING:
                            filesToCheck = manifest.files.mappings;
                            break;
                        case RESOURCE:
                            filesToCheck = manifest.files.resources;
                            break;
                        case THEME:
                            filesToCheck = manifest.files.themes;
                            break;
                    }

                    if (filesToCheck != null && filesToCheck.contains(filename)) {
                        return manifest.name;
                    }
                } catch (Exception e) {
                    // Skip invalid manifests
                }
            }
        }

        return null;
    }

    private ExtensionManifest validateExtension(ZipFile zipFile, Map<String, byte[]> fileContents) 
            throws IOException, ExtensionValidationException {

        ZipEntry manifestEntry = zipFile.getEntry(MANIFEST_FILENAME);
        if (manifestEntry == null) {
            throw new ExtensionValidationException(bundle.getString("error.extensionmgr.noManifest"));
        }

        ExtensionManifest manifest;
        try (InputStream is = zipFile.getInputStream(manifestEntry);
             Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            manifest = new Gson().fromJson(reader, ExtensionManifest.class);
        } catch (JsonSyntaxException e) {
            throw new ExtensionValidationException(MessageFormat.format(
                bundle.getString("error.extensionmgr.invalidManifest"),
                e.getMessage()
            ));
        }

        validateManifestStructure(manifest);

        Set<String> declaredFiles = new HashSet<>();
        if (manifest.files != null) {
            if (manifest.files.mappings != null) {
                declaredFiles.addAll(manifest.files.mappings);
            }
            if (manifest.files.resources != null) {
                declaredFiles.addAll(manifest.files.resources);
            }
            if (manifest.files.themes != null) {
                declaredFiles.addAll(manifest.files.themes);
            }
        }

        if (declaredFiles.isEmpty()) {
            throw new ExtensionValidationException(bundle.getString("error.extensionmgr.noFiles"));
        }

        for (String declaredFile : declaredFiles) {
            ZipEntry entry = zipFile.getEntry(declaredFile);
            if (entry == null) {
                throw new ExtensionValidationException(MessageFormat.format(
                    bundle.getString("error.extensionmgr.fileMissing"),
                    declaredFile
                ));
            }

            try (InputStream is = zipFile.getInputStream(entry)) {
                fileContents.put(declaredFile, is.readAllBytes());
            }
        }

        if (manifest.files != null && manifest.files.mappings != null) {
            for (String mappingFile : manifest.files.mappings) {
                if (!mappingFile.matches(".*_mapping\\.json")) {
                    throw new ExtensionValidationException(MessageFormat.format(
                        bundle.getString("error.extensionmgr.invalidMappingName"),
                        mappingFile
                    ));
                }
            }
        }

        return manifest;
    }

    private void validateManifestStructure(ExtensionManifest manifest) throws ExtensionValidationException {
        if (manifest.name == null || manifest.name.trim().isEmpty()) {
            throw new ExtensionValidationException(bundle.getString("error.extensionmgr.nameRequired"));
        }

        if (manifest.version == null || manifest.version.trim().isEmpty()) {
            throw new ExtensionValidationException(bundle.getString("error.extensionmgr.versionRequired"));
        }

        if (manifest.type == null || manifest.type.trim().isEmpty()) {
            throw new ExtensionValidationException(bundle.getString("error.extensionmgr.typeRequired"));
        }

        if (!ALLOWED_FILE_TYPES.containsKey(manifest.type)) {
            throw new ExtensionValidationException(MessageFormat.format(
                bundle.getString("error.extensionmgr.invalidType"),
                manifest.type,
                String.join(", ", ALLOWED_FILE_TYPES.keySet())
            ));
        }

        if (manifest.files == null) {
            throw new ExtensionValidationException(bundle.getString("error.extensionmgr.filesRequired"));
        }

        Set<String> allowedTypes = ALLOWED_FILE_TYPES.get(manifest.type);
        boolean hasFiles = false;

        if (manifest.files.mappings != null && !manifest.files.mappings.isEmpty()) {
            if (!allowedTypes.contains("mappings")) {
                throw new ExtensionValidationException(MessageFormat.format(
                    bundle.getString("error.extensionmgr.cannotContainMappings"),
                    manifest.type
                ));
            }
            hasFiles = true;
        }

        if (manifest.files.resources != null && !manifest.files.resources.isEmpty()) {
            if (!allowedTypes.contains("resources")) {
                throw new ExtensionValidationException(MessageFormat.format(
                    bundle.getString("error.extensionmgr.cannotContainResources"),
                    manifest.type
                ));
            }
            hasFiles = true;
        }

        if (manifest.files.themes != null && !manifest.files.themes.isEmpty()) {
            if (!allowedTypes.contains("themes")) {
                throw new ExtensionValidationException(MessageFormat.format(
                    bundle.getString("error.extensionmgr.cannotContainThemes"),
                    manifest.type
                ));
            }
            hasFiles = true;
        }

        if (!hasFiles) {
            throw new ExtensionValidationException(MessageFormat.format(
                bundle.getString("error.extensionmgr.mustDeclareFiles"),
                manifest.type,
                String.join(", ", allowedTypes)
            ));
        }
    }

    private void installFiles(ExtensionManifest manifest, Map<String, byte[]> fileContents) 
            throws IOException {
        Path configDir = configManager.getDefaultConfigDir();

        if (manifest.files == null) {
            return;
        }

        if (manifest.files.mappings != null) {
            for (String mappingFile : manifest.files.mappings) {
                Path targetPath = configDir.resolve(mappingFile);
                Files.write(targetPath, fileContents.get(mappingFile));
                if (verbosity > 0) {
                    System.out.println(MessageFormat.format(
                        bundle.getString("msg.extensionmgr.fileInstalled"), mappingFile));
                }
            }
        }

        if (manifest.files.resources != null) {
            for (String resourceFile : manifest.files.resources) {
                Path targetPath = i18nDir.resolve(resourceFile);
                Files.write(targetPath, fileContents.get(resourceFile));
                if (verbosity > 0) {
                    System.out.println(MessageFormat.format(
                        bundle.getString("msg.extensionmgr.fileInstalled"), resourceFile));
                }
            }
        }

        if (manifest.files.themes != null) {
            for (String themeFile : manifest.files.themes) {
                Path targetPath = guiThemesDir.resolve(themeFile);
                Files.write(targetPath, fileContents.get(themeFile));
                if (verbosity > 0) {
                    System.out.println(MessageFormat.format(
                        bundle.getString("msg.extensionmgr.fileInstalled"), themeFile));
                }
            }
        }
    }

    private void saveManifest(ExtensionManifest manifest) throws IOException {
        Path manifestPath = extensionsDir.resolve(manifest.name + ".json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(manifest);
        Files.writeString(manifestPath, json, StandardCharsets.UTF_8);
    }

    private ExtensionManifest loadManifest(Path manifestPath) throws IOException {
        String json = Files.readString(manifestPath, StandardCharsets.UTF_8);
        return new Gson().fromJson(json, ExtensionManifest.class);
    }

    public static class ExtensionManifest {
        public String name;
        public String version;
        public String type;
        public String description;
        public String author;
        public ExtensionFiles files;
    }

    public static class ExtensionFiles {
        public List<String> mappings;
        public List<String> resources;
        public List<String> themes;
    }

    public static class ExtensionRegistry {
        public String version;
        @SerializedName("last_updated")
        public String lastUpdated;
        public List<RegistryExtension> extensions;
    }

    public static class RegistryExtension {
        public String name;
        public String version;
        public String type;
        public String description;
        public String author;
        public ExtensionFiles files;
        @SerializedName("download_url")
        public String downloadUrl;
        public Long size;
        public String sha256;
        @SerializedName("published_date")
        public String publishedDate;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%s v%s (%s)", name, version, type));
            if (description != null) {
                sb.append(" - ").append(description);
            }
            if (author != null) {
                sb.append(" (by ").append(author).append(")");
            }
            return sb.toString();
        }
    }

    public static class ExtensionInfo {
        public final ExtensionManifest manifest;
        public final Path manifestPath;

        public ExtensionInfo(ExtensionManifest manifest, Path manifestPath) {
            this.manifest = manifest;
            this.manifestPath = manifestPath;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%s v%s", manifest.name, manifest.version));
            if (manifest.type != null) {
                sb.append(String.format(" (%s)", manifest.type));
            }
            if (manifest.description != null) {
                sb.append(" - ").append(manifest.description);
            }
            if (manifest.author != null) {
                sb.append(" (by ").append(manifest.author).append(")");
            }
            return sb.toString();
        }
    }

    public static class ExtensionValidationException extends Exception {
        public ExtensionValidationException(String message) {
            super(message);
        }
    }

    private enum FileType {
        MAPPING,
        RESOURCE,
        THEME
    }
}
