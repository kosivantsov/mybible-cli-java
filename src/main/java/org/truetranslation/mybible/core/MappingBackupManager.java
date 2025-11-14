package org.truetranslation.mybible.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

public class MappingBackupManager {

    private static final String BACKUP_FOLDER = "backup";
    private static final String MAPPING_FILENAME = "default_mapping.json";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    public static void performSilentBackup(Path configDir) {
        try {
            Path mappingFile = configDir.resolve(MAPPING_FILENAME);

            if (!Files.exists(mappingFile)) {
                return;
            }

            Path backupDir = configDir.resolve(BACKUP_FOLDER);
            if (!Files.exists(backupDir)) {
                Files.createDirectories(backupDir);
            }

            Optional<Path> latestBackup = getLatestBackupFile(backupDir);

            boolean needsBackup = true;
            if (latestBackup.isPresent()) {
                needsBackup = !areFilesIdentical(mappingFile, latestBackup.get());
            }

            if (needsBackup) {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String backupFilename = timestamp + "_" + MAPPING_FILENAME;
                Path backupFile = backupDir.resolve(backupFilename);

                Files.copy(mappingFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (IOException e) {
        }
    }

    private static Optional<Path> getLatestBackupFile(Path backupDir) throws IOException {
        if (!Files.exists(backupDir) || !Files.isDirectory(backupDir)) {
            return Optional.empty();
        }

        try (Stream<Path> files = Files.list(backupDir)) {
            return files
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith("_" + MAPPING_FILENAME))
                .filter(path -> isValidTimestampFilename(path.getFileName().toString()))
                .max(Comparator.comparing(path -> extractTimestamp(path.getFileName().toString())));
        }
    }

    private static boolean isValidTimestampFilename(String filename) {
        if (!filename.endsWith("_" + MAPPING_FILENAME)) {
            return false;
        }

        String timestampPart = filename.substring(0, filename.length() - ("_" + MAPPING_FILENAME).length());

        return timestampPart.matches("^\\d{12}$");
    }

    private static String extractTimestamp(String filename) {
        return filename.substring(0, filename.length() - ("_" + MAPPING_FILENAME).length());
    }

    private static boolean areFilesIdentical(Path file1, Path file2) throws IOException {
        if (!Files.exists(file1) || !Files.exists(file2)) {
            return false;
        }

        if (Files.size(file1) != Files.size(file2)) {
            return false;
        }

        byte[] content1 = Files.readAllBytes(file1);
        byte[] content2 = Files.readAllBytes(file2);

        return Arrays.equals(content1, content2);
    }

    public static java.util.List<Path> getAllBackupFiles(Path configDir) throws IOException {
        Path backupDir = configDir.resolve(BACKUP_FOLDER);

        if (!Files.exists(backupDir) || !Files.isDirectory(backupDir)) {
            return java.util.Collections.emptyList();
        }

        try (Stream<Path> files = Files.list(backupDir)) {
            return files
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith("_" + MAPPING_FILENAME))
                .filter(path -> isValidTimestampFilename(path.getFileName().toString()))
                .sorted(Comparator.comparing(path -> extractTimestamp(path.getFileName().toString())))
                .collect(java.util.stream.Collectors.toList());
        }
    }

    public static void cleanupOldBackups(Path configDir, int keepCount) throws IOException {
        java.util.List<Path> allBackups = getAllBackupFiles(configDir);

        if (allBackups.size() <= keepCount) {
            return;
        }

        int toDelete = allBackups.size() - keepCount;
        for (int i = 0; i < toDelete; i++) {
            Files.deleteIfExists(allBackups.get(i));
        }
    }
}
