package me.kitakeyos.plugin.updater;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.kitakeyos.plugin.api.PluginMetadata;
import me.kitakeyos.plugin.config.UpdateConfig;
import me.kitakeyos.plugin.manager.PluginLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Enhanced standalone plugin update manager with flexible update options
 * Handles checking, applying, and rolling back plugin updates independently
 */
@Slf4j
public class PluginUpdateManager {
    private final File pluginDirectory;
    private final File updateDirectory;
    private final File backupDirectory;
    private final PluginLoader loader;
    private final ExecutorService updateExecutor;

    // Update configuration
    @Getter
    private UpdateConfig config;

    // Progress callbacks
    private Consumer<UpdateProgress> progressCallback;
    private Consumer<String> logCallback;

    public PluginUpdateManager(File pluginDirectory, File updateDirectory, PluginLoader loader) {
        this(pluginDirectory, updateDirectory, loader, UpdateConfig.defaultConfig());
    }

    public PluginUpdateManager(File pluginDirectory, File updateDirectory, PluginLoader loader, UpdateConfig config) {
        this.pluginDirectory = Objects.requireNonNull(pluginDirectory, "Plugin directory cannot be null");
        this.updateDirectory = Objects.requireNonNull(updateDirectory, "Update directory cannot be null");
        this.loader = Objects.requireNonNull(loader, "Plugin loader cannot be null");
        this.config = Objects.requireNonNull(config, "Update config cannot be null");

        this.backupDirectory = new File(pluginDirectory.getParent(), "plugin-backups");
        this.updateExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PluginUpdater");
            t.setDaemon(true);
            return t;
        });

        ensureDirectoriesExist();
        log.info("PluginUpdateManager initialized with config: {}", config);
    }

    /**
     * Sets progress callback for update operations
     */
    public void setProgressCallback(Consumer<UpdateProgress> callback) {
        this.progressCallback = callback;
    }

    /**
     * Sets log callback for update operations
     */
    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    /**
     * Updates the configuration
     */
    public void updateConfig(UpdateConfig newConfig) {
        this.config = Objects.requireNonNull(newConfig, "Update config cannot be null");
        logMessage("Updated configuration: " + newConfig);
    }

    /**
     * Scans for available updates without applying them
     */
    public UpdateScanResult scanForUpdates() {
        logMessage("Scanning for plugin updates...");
        notifyProgress(UpdateProgress.scanning());

        File[] updateFiles = updateDirectory.listFiles((dir, name) -> name.endsWith(".jar"));
        if (updateFiles == null || updateFiles.length == 0) {
            logMessage("No update files found");
            notifyProgress(UpdateProgress.completed(UpdateResult.noUpdates()));
            return UpdateScanResult.noUpdates();
        }

        List<UpdateInfo> availableUpdates = new ArrayList<>();
        List<String> invalidFiles = new ArrayList<>();

        for (File updateFile : updateFiles) {
            try {
                UpdateInfo updateInfo = analyzeUpdateFile(updateFile);
                if (updateInfo != null) {
                    availableUpdates.add(updateInfo);
                } else {
                    invalidFiles.add(updateFile.getName());
                }
            } catch (Exception e) {
                logMessage("Failed to analyze update file: " + updateFile.getName() + " - " + e.getMessage());
                invalidFiles.add(updateFile.getName());
            }
        }

        UpdateScanResult result = new UpdateScanResult(availableUpdates, invalidFiles);
        logMessage(String.format("Scan completed: %d updates available, %d invalid files",
                availableUpdates.size(), invalidFiles.size()));

        return result;
    }

    /**
     * Applies all available updates with current configuration
     */
    public UpdateResult applyAllUpdates() {
        return applyUpdates(scanForUpdates().getAvailableUpdates());
    }

    /**
     * Applies specific updates
     */
    public UpdateResult applyUpdates(List<UpdateInfo> updates) {
        if (updates.isEmpty()) {
            return UpdateResult.noUpdates();
        }

        logMessage(String.format("Applying %d plugin updates...", updates.size()));
        notifyProgress(UpdateProgress.applying(updates.size()));

        List<String> successful = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        Map<String, File> backups = new HashMap<>();

        int current = 0;
        for (UpdateInfo updateInfo : updates) {
            current++;
            notifyProgress(UpdateProgress.processing(updateInfo.getPluginName(), current, updates.size()));

            try {
                if (processUpdate(updateInfo, backups)) {
                    successful.add(updateInfo.getPluginName());
                    logMessage(String.format("Successfully updated: %s v%s",
                            updateInfo.getPluginName(), updateInfo.getNewVersion()));
                } else {
                    failed.add(updateInfo.getPluginName());
                }
            } catch (Exception e) {
                logMessage("Failed to update " + updateInfo.getPluginName() + ": " + e.getMessage());
                failed.add(updateInfo.getPluginName());
                restoreFromBackup(updateInfo.getPluginName(), backups.get(updateInfo.getPluginName()));
            }
        }

        // Cleanup
        if (config.isCleanupUpdateFiles()) {
            cleanupProcessedUpdates(updates, successful);
        }

        if (config.isAutoCleanupBackups()) {
            cleanupSuccessfulBackups(successful, backups);
        }

        UpdateResult result = new UpdateResult(successful, failed);
        logMessage(String.format("Update completed: %d successful, %d failed",
                successful.size(), failed.size()));
        notifyProgress(UpdateProgress.completed(result));

        return result;
    }

    /**
     * Applies updates asynchronously
     */
    public CompletableFuture<UpdateResult> applyUpdatesAsync(List<UpdateInfo> updates) {
        return CompletableFuture.supplyAsync(() -> applyUpdates(updates), updateExecutor);
    }

    /**
     * Applies all available updates asynchronously
     */
    public CompletableFuture<UpdateResult> applyAllUpdatesAsync() {
        return CompletableFuture.supplyAsync(() -> {
            UpdateScanResult scan = scanForUpdates();
            return applyUpdates(scan.getAvailableUpdates());
        }, updateExecutor);
    }

    /**
     * Rollback a plugin to its backup
     */
    public boolean rollbackPlugin(String pluginName) {
        File[] backupFiles = backupDirectory.listFiles((dir, name) ->
                name.startsWith(pluginName + "-") && name.endsWith("-backup.jar"));

        if (backupFiles == null || backupFiles.length == 0) {
            logMessage("No backup found for plugin: " + pluginName);
            return false;
        }

        // Find the most recent backup
        File mostRecentBackup = Arrays.stream(backupFiles)
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null);

        File currentPlugin = findExistingPluginFile(pluginName);
        if (currentPlugin != null) {
            try {
                Files.copy(mostRecentBackup.toPath(), currentPlugin.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                logMessage("Successfully rolled back plugin: " + pluginName);
                return true;
            } catch (IOException e) {
                logMessage("Failed to rollback plugin " + pluginName + ": " + e.getMessage());
            }
        }

        return false;
    }

    /**
     * Lists all available backups
     */
    public List<BackupInfo> listBackups() {
        File[] backupFiles = backupDirectory.listFiles((dir, name) -> name.endsWith("-backup.jar"));
        if (backupFiles == null) {
            return Collections.emptyList();
        }

        return Arrays.stream(backupFiles)
                .map(this::parseBackupInfo)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(BackupInfo::getTimestamp).reversed()).collect(Collectors.toList());
    }

    /**
     * Cleans up old backups based on configuration
     */
    public int cleanupOldBackups() {
        if (config.getMaxBackupAge() <= 0) {
            return 0;
        }

        long cutoffTime = System.currentTimeMillis() - config.getMaxBackupAge();
        File[] backupFiles = backupDirectory.listFiles((dir, name) -> name.endsWith("-backup.jar"));

        if (backupFiles == null) {
            return 0;
        }

        int cleaned = 0;
        for (File backup : backupFiles) {
            if (backup.lastModified() < cutoffTime) {
                if (backup.delete()) {
                    cleaned++;
                    logMessage("Cleaned up old backup: " + backup.getName());
                }
            }
        }

        logMessage("Cleaned up " + cleaned + " old backups");
        return cleaned;
    }

    /**
     * Gets update statistics
     */
    public UpdateStats getUpdateStats() {
        File[] updateFiles = updateDirectory.listFiles((dir, name) -> name.endsWith(".jar"));
        File[] backupFiles = backupDirectory.listFiles((dir, name) -> name.endsWith("-backup.jar"));

        long updateDirectorySize = calculateDirectorySize(updateDirectory);
        long backupDirectorySize = calculateDirectorySize(backupDirectory);

        return new UpdateStats(
                updateFiles != null ? updateFiles.length : 0,
                backupFiles != null ? backupFiles.length : 0,
                updateDirectorySize,
                backupDirectorySize
        );
    }

    // Private helper methods

    private UpdateInfo analyzeUpdateFile(File updateFile) throws Exception {
        PluginMetadata metadata = loader.loadMetadata(updateFile);
        String pluginName = metadata.getName();
        String newVersion = metadata.getVersion();

        File existingPlugin = findExistingPluginFile(pluginName);

        if (existingPlugin != null) {
            // Existing plugin update
            String currentVersion = null;
            try {
                PluginMetadata existingMetadata = loader.loadMetadata(existingPlugin);
                currentVersion = existingMetadata.getVersion();
            } catch (Exception e) {
                logMessage("Could not read current version for " + pluginName);
            }

            boolean isNewer = currentVersion == null ||
                    !config.isCheckVersionConstraints() ||
                    isVersionNewer(newVersion, currentVersion);

            UpdateType type = isNewer ? UpdateType.UPDATE : UpdateType.DOWNGRADE;
            if (!isNewer && config.isCheckVersionConstraints()) {
                logMessage(String.format("Skipping %s: version %s is not newer than %s",
                        pluginName, newVersion, currentVersion));
                return null;
            }

            return new UpdateInfo(pluginName, currentVersion, newVersion, updateFile, type);
        } else {
            // New plugin installation
            return new UpdateInfo(pluginName, null, newVersion, updateFile, UpdateType.INSTALL);
        }
    }

    private boolean processUpdate(UpdateInfo updateInfo, Map<String, File> backups) {
        File targetFile;

        if (updateInfo.getType() == UpdateType.INSTALL) {
            targetFile = new File(pluginDirectory, updateInfo.getUpdateFile().getName());
        } else {
            targetFile = findExistingPluginFile(updateInfo.getPluginName());
            if (targetFile == null) {
                logMessage("Target plugin file not found for: " + updateInfo.getPluginName());
                return false;
            }
        }

        // Create backup for existing plugins
        if (updateInfo.getType() != UpdateType.INSTALL && config.isCreateBackups()) {
            File backup = createBackup(targetFile, updateInfo.getPluginName());
            if (backup != null) {
                backups.put(updateInfo.getPluginName(), backup);
            }
        }

        // Perform update
        return copyFile(updateInfo.getUpdateFile(), targetFile);
    }

    private File createBackup(File pluginFile, String pluginName) {
        if (!config.isCreateBackups()) {
            return null;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        File backupFile = new File(backupDirectory, pluginName + "-" + timestamp + "-backup.jar");

        try {
            Files.copy(pluginFile.toPath(), backupFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            logMessage("Created backup: " + backupFile.getName());
            return backupFile;
        } catch (IOException e) {
            logMessage("Failed to create backup for " + pluginName + ": " + e.getMessage());
            return null;
        }
    }

    private void restoreFromBackup(String pluginName, File backupFile) {
        if (backupFile == null || !backupFile.exists()) {
            logMessage("No backup found for plugin: " + pluginName);
            return;
        }

        File pluginFile = findExistingPluginFile(pluginName);
        if (pluginFile != null) {
            try {
                Files.copy(backupFile.toPath(), pluginFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logMessage("Restored plugin from backup: " + pluginName);
            } catch (IOException e) {
                logMessage("Failed to restore plugin from backup " + pluginName + ": " + e.getMessage());
            }
        }
    }

    private File findExistingPluginFile(String pluginName) {
        File[] jarFiles = pluginDirectory.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jarFiles == null) return null;

        for (File jarFile : jarFiles) {
            try {
                PluginMetadata metadata = loader.loadMetadata(jarFile);
                if (pluginName.equals(metadata.getName())) {
                    return jarFile;
                }
            } catch (Exception e) {
                log.debug("Could not load metadata from {}: {}", jarFile.getName(), e.getMessage());
            }
        }
        return null;
    }

    private boolean copyFile(File source, File destination) {
        try {
            Files.copy(source.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
            return true;
        } catch (IOException e) {
            logMessage("Failed to copy file from " + source.getName() + " to " + destination.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private void cleanupProcessedUpdates(List<UpdateInfo> updates, List<String> successful) {
        for (UpdateInfo update : updates) {
            if (successful.contains(update.getPluginName())) {
                try {
                    if (update.getUpdateFile().delete()) {
                        logMessage("Cleaned up update file: " + update.getUpdateFile().getName());
                    }
                } catch (Exception e) {
                    logMessage("Failed to cleanup update file: " + update.getUpdateFile().getName());
                }
            }
        }
    }

    private void cleanupSuccessfulBackups(List<String> successful, Map<String, File> backups) {
        for (String pluginName : successful) {
            File backup = backups.get(pluginName);
            if (backup != null && backup.exists()) {
                if (backup.delete()) {
                    logMessage("Cleaned up backup for successful update: " + pluginName);
                }
            }
        }
    }

    private boolean isVersionNewer(String version1, String version2) {
        try {
            String[] v1Parts = version1.split("\\.");
            String[] v2Parts = version2.split("\\.");

            int maxLength = Math.max(v1Parts.length, v2Parts.length);

            for (int i = 0; i < maxLength; i++) {
                int v1Part = i < v1Parts.length ? Integer.parseInt(v1Parts[i]) : 0;
                int v2Part = i < v2Parts.length ? Integer.parseInt(v2Parts[i]) : 0;

                if (v1Part > v2Part) return true;
                if (v1Part < v2Part) return false;
            }

            return false;
        } catch (NumberFormatException e) {
            logMessage("Could not parse versions for comparison: " + version1 + " vs " + version2);
            return !config.isCheckVersionConstraints(); // Allow if version checking is disabled
        }
    }

    private BackupInfo parseBackupInfo(File backupFile) {
        String name = backupFile.getName();
        try {
            // Format: pluginName-timestamp-backup.jar
            String[] parts = name.replace("-backup.jar", "").split("-");
            if (parts.length >= 2) {
                String pluginName = String.join("-", Arrays.copyOf(parts, parts.length - 1));
                String timestamp = parts[parts.length - 1];
                return new BackupInfo(pluginName, timestamp, backupFile);
            }
        } catch (Exception e) {
            logMessage("Failed to parse backup info from: " + name);
        }
        return null;
    }

    private long calculateDirectorySize(File directory) {
        try {
            return Files.walk(directory.toPath())
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    private void ensureDirectoriesExist() {
        try {
            Files.createDirectories(pluginDirectory.toPath());
            Files.createDirectories(updateDirectory.toPath());
            Files.createDirectories(backupDirectory.toPath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create required directories", e);
        }
    }

    private void logMessage(String message) {
        log.info(message);
        if (logCallback != null) {
            logCallback.accept(message);
        }
    }

    private void notifyProgress(UpdateProgress progress) {
        if (progressCallback != null) {
            progressCallback.accept(progress);
        }
    }

    public void shutdown() {
        updateExecutor.shutdown();
        log.debug("PluginUpdateManager shut down");
    }
}