package org.truetranslation.mybible.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    private final ResourceBundle bundle;
    private final int verbosity;

    private static final String MANIFEST_FILENAME = "manifest.json";
    private static final Set<String> PROTECTED_FILES = Set.of("default_mapping.json");

    public ExtensionManager(ConfigManager configManager, int verbosity) {
        this.configManager = configManager;
        this.verbosity = verbosity;

        Path configDir = configManager.getDefaultConfigDir();
        this.extensionsDir = configDir.resolve("ext");
        this.resourcesDir = configDir.resolve("resources");
        this.i18nDir = resourcesDir.resolve("i18n");
        this.guiThemesDir = configDir.resolve("gui_themes");

        ExternalResourceBundleLoader loader = new ExternalResourceBundleLoader(configDir);
        this.bundle = loader.getBundle("i18n.messages");
    }

    // List all installed extensions.
    public List<ExtensionInfo> listExtensions() throws IOException {
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
                            bundle.getString("error.extension.manifestLoad"),
                            manifestPath.getFileName().toString(),
                            e.getMessage()
                        ));
                    }
                }
            }
        }

        return extensions;
    }

    // Install an extension from a zip file.
    public void installExtension(Path zipPath) throws IOException, ExtensionValidationException {
        if (!Files.exists(zipPath)) {
            throw new IOException(MessageFormat.format(
                bundle.getString("error.extension.fileNotFound"),
                zipPath.toString()
            ));
        }

        if (!zipPath.toString().toLowerCase().endsWith(".zip")) {
            throw new ExtensionValidationException(
                bundle.getString("error.extension.notZip")
            );
        }

        // Step 1: Validate the extension
        ExtensionManifest manifest;
        Map<String, byte[]> fileContents = new HashMap<>();

        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            manifest = validateExtension(zipFile, fileContents);
        }

        // Step 2: Check for file conflicts
        checkFileConflicts(manifest);

        // Step 3: Create directories if needed
        Files.createDirectories(extensionsDir);
        Files.createDirectories(i18nDir);
        Files.createDirectories(guiThemesDir);

        // Step 4: Install the extension files
        installFiles(manifest, fileContents);

        // Step 5: Save the manifest
        saveManifest(manifest);

        if (verbosity > 0) {
            System.out.println(MessageFormat.format(
                bundle.getString("msg.extension.installed"),
                manifest.name,
                manifest.version
            ));
        }
    }

    // Uninstalls an extension by name.
    public void uninstallExtension(String extensionName) throws IOException {
        if (!Files.exists(extensionsDir)) {
            throw new IOException(bundle.getString("error.extension.noneInstalled"));
        }

        // Find the manifest file
        Path manifestPath = extensionsDir.resolve(extensionName + ".json");
        if (!Files.exists(manifestPath)) {
            throw new IOException(MessageFormat.format(
                bundle.getString("error.extension.notFound"),
                extensionName
            ));
        }

        // Load manifest to know what files to delete
        ExtensionManifest manifest = loadManifest(manifestPath);

        // Delete mapping files
        if (manifest.mappingFiles != null) {
            for (String mappingFile : manifest.mappingFiles) {
                Path mappingPath = configManager.getDefaultConfigDir().resolve(mappingFile);
                if (Files.exists(mappingPath)) {
                    Files.delete(mappingPath);
                    if (verbosity > 0) {
                        System.out.println(MessageFormat.format(
                            bundle.getString("msg.extension.fileDeleted"),
                            mappingFile
                        ));
                    }
                }
            }
        }

        // Delete resource files
        if (manifest.resourceFiles != null) {
            for (String resourceFile : manifest.resourceFiles) {
                Path resourcePath = i18nDir.resolve(resourceFile);
                if (Files.exists(resourcePath)) {
                    Files.delete(resourcePath);
                    if (verbosity > 0) {
                        System.out.println(MessageFormat.format(
                            bundle.getString("msg.extension.fileDeleted"),
                            resourceFile
                        ));
                    }
                }
            }
        }

        // Delete theme files
        if (manifest.themeFiles != null) {
            for (String themeFile : manifest.themeFiles) {
                Path themePath = guiThemesDir.resolve(themeFile);
                if (Files.exists(themePath)) {
                    Files.delete(themePath);
                    if (verbosity > 0) {
                        System.out.println(MessageFormat.format(
                            bundle.getString("msg.extension.fileDeleted"),
                            themeFile
                        ));
                    }
                }
            }
        }

        // Delete manifest
        Files.delete(manifestPath);

        if (verbosity > 0) {
            System.out.println(MessageFormat.format(
                bundle.getString("msg.extension.uninstalled"),
                manifest.name
            ));
        }
    }

    // Checks for file conflicts before installation.
    private void checkFileConflicts(ExtensionManifest manifest) 
            throws IOException, ExtensionValidationException {

        List<String> conflicts = new ArrayList<>();
        Path configDir = configManager.getDefaultConfigDir();

        // Check mapping files
        if (manifest.mappingFiles != null) {
            for (String mappingFile : manifest.mappingFiles) {
                // Check if it's a protected file
                if (PROTECTED_FILES.contains(mappingFile)) {
                    conflicts.add(MessageFormat.format(
                        bundle.getString("error.extension.protectedFile"),
                        mappingFile
                    ));
                    continue;
                }

                // Check if owned by another extension
                Path targetPath = configDir.resolve(mappingFile);
                if (Files.exists(targetPath)) {
                    String owner = findFileOwner(mappingFile, FileType.MAPPING);
                    if (owner != null && !owner.equals(manifest.name)) {
                        conflicts.add(MessageFormat.format(
                            bundle.getString("error.extension.fileConflict"),
                            mappingFile,
                            owner
                        ));
                    }
                }
            }
        }

        // Check resource files
        if (manifest.resourceFiles != null) {
            for (String resourceFile : manifest.resourceFiles) {
                Path targetPath = i18nDir.resolve(resourceFile);
                if (Files.exists(targetPath)) {
                    String owner = findFileOwner(resourceFile, FileType.RESOURCE);
                    if (owner != null && !owner.equals(manifest.name)) {
                        conflicts.add(MessageFormat.format(
                            bundle.getString("error.extension.fileConflict"),
                            resourceFile,
                            owner
                        ));
                    }
                }
            }
        }

        // Check theme files
        if (manifest.themeFiles != null) {
            for (String themeFile : manifest.themeFiles) {
                Path targetPath = guiThemesDir.resolve(themeFile);
                if (Files.exists(targetPath)) {
                    String owner = findFileOwner(themeFile, FileType.THEME);
                    if (owner != null && !owner.equals(manifest.name)) {
                        conflicts.add(MessageFormat.format(
                            bundle.getString("error.extension.fileConflict"),
                            themeFile,
                            owner
                        ));
                    }
                }
            }
        }

        if (!conflicts.isEmpty()) {
            throw new ExtensionValidationException(
                bundle.getString("error.extension.conflictsFound") + "\n" +
                String.join("\n", conflicts)
            );
        }
    }

    // Finds which extension owns a specific file.
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

                    List<String> filesToCheck = null;
                    switch (fileType) {
                        case MAPPING:
                            filesToCheck = manifest.mappingFiles;
                            break;
                        case RESOURCE:
                            filesToCheck = manifest.resourceFiles;
                            break;
                        case THEME:
                            filesToCheck = manifest.themeFiles;
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

    // Validates an extension zip file and reads its contents.
    private ExtensionManifest validateExtension(ZipFile zipFile, Map<String, byte[]> fileContents) 
            throws IOException, ExtensionValidationException {

        // Step 1: Check for manifest
        ZipEntry manifestEntry = zipFile.getEntry(MANIFEST_FILENAME);
        if (manifestEntry == null) {
            throw new ExtensionValidationException(
                bundle.getString("error.extension.noManifest")
            );
        }

        // Step 2: Parse manifest
        ExtensionManifest manifest;
        try (InputStream is = zipFile.getInputStream(manifestEntry);
             Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            manifest = new Gson().fromJson(reader, ExtensionManifest.class);
        } catch (JsonSyntaxException e) {
            throw new ExtensionValidationException(MessageFormat.format(
                bundle.getString("error.extension.invalidManifest"),
                e.getMessage()
            ));
        }

        // Step 3: Validate manifest content
        if (manifest.name == null || manifest.name.trim().isEmpty()) {
            throw new ExtensionValidationException(
                bundle.getString("error.extension.noName")
            );
        }

        if (manifest.version == null || manifest.version.trim().isEmpty()) {
            throw new ExtensionValidationException(
                bundle.getString("error.extension.noVersion")
            );
        }

        // Step 4: Collect all declared files
        Set<String> declaredFiles = new HashSet<>();
        if (manifest.mappingFiles != null) {
            declaredFiles.addAll(manifest.mappingFiles);
        }
        if (manifest.resourceFiles != null) {
            declaredFiles.addAll(manifest.resourceFiles);
        }
        if (manifest.themeFiles != null) {
            declaredFiles.addAll(manifest.themeFiles);
        }

        if (declaredFiles.isEmpty()) {
            throw new ExtensionValidationException(
                bundle.getString("error.extension.noFiles")
            );
        }

        // Step 5: Verify all declared files exist in zip
        for (String declaredFile : declaredFiles) {
            ZipEntry entry = zipFile.getEntry(declaredFile);
            if (entry == null) {
                throw new ExtensionValidationException(MessageFormat.format(
                    bundle.getString("error.extension.fileMissing"),
                    declaredFile
                ));
            }

            // Read file content
            try (InputStream is = zipFile.getInputStream(entry)) {
                fileContents.put(declaredFile, is.readAllBytes());
            }
        }

        // Step 6: Validate file naming conventions
        if (manifest.mappingFiles != null) {
            for (String mappingFile : manifest.mappingFiles) {
                if (!mappingFile.matches(".*_mapping\\.json")) {
                    throw new ExtensionValidationException(MessageFormat.format(
                        bundle.getString("error.extension.invalidMappingName"),
                        mappingFile
                    ));
                }
            }
        }

        if (manifest.resourceFiles != null) {
            for (String resourceFile : manifest.resourceFiles) {
                if (!resourceFile.matches("(messages|gui)_[a-z]{2}(_[A-Z]{2})?\\.properties")) {
                    throw new ExtensionValidationException(MessageFormat.format(
                        bundle.getString("error.extension.invalidResourceName"),
                        resourceFile
                    ));
                }
            }
        }

        if (manifest.themeFiles != null) {
            for (String themeFile : manifest.themeFiles) {
                if (!themeFile.endsWith(".json")) {
                    throw new ExtensionValidationException(MessageFormat.format(
                        bundle.getString("error.extension.invalidThemeName"),
                        themeFile
                    ));
                }
            }
        }

        return manifest;
    }

    /**
     * Installs files from the validated extension.
     */
    private void installFiles(ExtensionManifest manifest, Map<String, byte[]> fileContents) 
            throws IOException {

        Path configDir = configManager.getDefaultConfigDir();

        // Install mapping files
        if (manifest.mappingFiles != null) {
            for (String mappingFile : manifest.mappingFiles) {
                Path targetPath = configDir.resolve(mappingFile);
                Files.write(targetPath, fileContents.get(mappingFile));
                if (verbosity > 0) {
                    System.out.println(MessageFormat.format(
                        bundle.getString("msg.extension.fileInstalled"),
                        mappingFile
                    ));
                }
            }
        }

        // Install resource files
        if (manifest.resourceFiles != null) {
            for (String resourceFile : manifest.resourceFiles) {
                Path targetPath = i18nDir.resolve(resourceFile);
                Files.write(targetPath, fileContents.get(resourceFile));
                if (verbosity > 0) {
                    System.out.println(MessageFormat.format(
                        bundle.getString("msg.extension.fileInstalled"),
                        resourceFile
                    ));
                }
            }
        }

        // Install theme files
        if (manifest.themeFiles != null) {
            for (String themeFile : manifest.themeFiles) {
                Path targetPath = guiThemesDir.resolve(themeFile);
                Files.write(targetPath, fileContents.get(themeFile));
                if (verbosity > 0) {
                    System.out.println(MessageFormat.format(
                        bundle.getString("msg.extension.fileInstalled"),
                        themeFile
                    ));
                }
            }
        }
    }

    // Save the manifest to the extensions directory.
    private void saveManifest(ExtensionManifest manifest) throws IOException {
        Path manifestPath = extensionsDir.resolve(manifest.name + ".json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(manifest);
        Files.writeString(manifestPath, json, StandardCharsets.UTF_8);
    }

    // Loads a manifest from a file.
    private ExtensionManifest loadManifest(Path manifestPath) throws IOException {
        String json = Files.readString(manifestPath, StandardCharsets.UTF_8);
        return new Gson().fromJson(json, ExtensionManifest.class);
    }

    // Extension manifest structure.
    public static class ExtensionManifest {
        public String name;
        public String version;
        public String description;
        public String author;

        @SerializedName("mapping_files")
        public List<String> mappingFiles;

        @SerializedName("resource_files")
        public List<String> resourceFiles;

        @SerializedName("theme_files")
        public List<String> themeFiles;
    }

    // Information about an installed extension.
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
            if (manifest.description != null) {
                sb.append(" - ").append(manifest.description);
            }
            if (manifest.author != null) {
                sb.append(" (by ").append(manifest.author).append(")");
            }
            return sb.toString();
        }
    }

    // Exception for extension validation errors.
    public static class ExtensionValidationException extends Exception {
        public ExtensionValidationException(String message) {
            super(message);
        }
    }

    // File type enumeration for conflict checking.
    private enum FileType {
        MAPPING,
        RESOURCE,
        THEME
    }
}
