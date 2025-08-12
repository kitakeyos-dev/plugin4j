package me.kitakeyos.plugin.manager;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.kitakeyos.plugin.api.PluginMetadata;
import me.kitakeyos.plugin.exceptions.PluginLoadException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages plugin updates independently from core plugin management
 * Handles checking, applying, and rolling back plugin updates
 */
@Slf4j
public class PluginUpdateManager {
    private final File pluginDirectory;
    private final File updateDirectory;
    private final File backupDirectory;
    private final PluginLoader loader;

    // Thread pool for async operations
    private final ExecutorService updateExecutor;

    public PluginUpdateManager(File pluginDirectory, File updateDirectory, PluginLoader loader) {
        this.pluginDirectory = Objects.requireNonNull(pluginDirectory, "Plugin directory cannot be null");
        this.updateDirectory = Objects.requireNonNull(updateDirectory, "Update directory cannot be null");
        this.loader = Objects.requireNonNull(loader, "Plugin loader cannot be null");

        // Create backup directory
        this.backupDirectory = new File(pluginDirectory.getParent(), "plugin-backups");

        // Single thread executor for sequential updates
        this.updateExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PluginUpdater");
            t.setDaemon(true);
            return t;
        });

        ensureDirectoriesExist();
    }

    /**
     * Checks and applies plugin updates synchronously
     *
     * @return UpdateResult containing details of the update process
     */
    public UpdateResult checkAndApplyUpdates() {
        log.info("Checking for plugin updates in: {}", updateDirectory.getAbsolutePath());

        File[] updateFiles = updateDirectory.listFiles((dir, name) -> name.endsWith(".jar"));
        if (updateFiles == null || updateFiles.length == 0) {
            log.debug("No plugin updates found");
            return UpdateResult.noUpdates();
        }

        List<UpdateOperation> operations = new ArrayList<>();
        List<String> failedUpdates = new ArrayList<>();

        // Process each update file
        for (File updateFile : updateFiles) {
            try {
                UpdateOperation operation = processUpdateFile(updateFile);
                if (operation != null) {
                    operations.add(operation);
                } else {
                    failedUpdates.add(updateFile.getName());
                }
            } catch (Exception e) {
                log.error("Failed to process update file: {} - {}", updateFile.getName(), e.getMessage());
                failedUpdates.add(updateFile.getName());
            }
        }

        // Apply successful operations
        List<String> successful = applyUpdateOperations(operations);

        // Clean up update files
        cleanupUpdateFiles(updateFiles);

        if (!successful.isEmpty()) {
            log.info("Applied {} plugin updates: {}", successful.size(), successful);
        }
        if (!failedUpdates.isEmpty()) {
            log.warn("Failed to process {} updates: {}", failedUpdates.size(), failedUpdates);
        }

        return new UpdateResult(successful, failedUpdates);
    }

    /**
     * Checks and applies plugin updates asynchronously
     *
     * @return CompletableFuture with UpdateResult
     */
    public CompletableFuture<UpdateResult> checkAndApplyUpdatesAsync() {
        return CompletableFuture.supplyAsync(this::checkAndApplyUpdates, updateExecutor);
    }

    /**
     * Processes a single update file and creates an update operation
     *
     * @param updateFile Update file to process
     * @return UpdateOperation or null if processing failed
     */
    private UpdateOperation processUpdateFile(File updateFile) {
        PluginMetadata updateMetadata;
        try {
            updateMetadata = loader.loadMetadata(updateFile);
        } catch (Exception e) {
            throw new PluginLoadException(updateFile.getAbsolutePath(),
                    "Failed to load metadata from update file", e);
        }

        String pluginName = updateMetadata.getName();
        String version = updateMetadata.getVersion();

        // Check if this is an update or new plugin
        File existingPlugin = findExistingPluginFile(pluginName);

        if (existingPlugin != null) {
            // Verify version is actually newer
            try {
                PluginMetadata existingMetadata = loader.loadMetadata(existingPlugin);
                if (!isVersionNewer(version, existingMetadata.getVersion())) {
                    log.info("Skipping update for {}: version {} is not newer than {}",
                            pluginName, version, existingMetadata.getVersion());
                    return null;
                }
            } catch (Exception e) {
                log.warn("Could not read existing plugin metadata for {}, proceeding with update", pluginName);
            }

            return new UpdateOperation(pluginName, version, updateFile, existingPlugin, false);
        } else {
            // New plugin
            File targetFile = new File(pluginDirectory, updateFile.getName());
            return new UpdateOperation(pluginName, version, updateFile, targetFile, true);
        }
    }

    /**
     * Applies a list of update operations with rollback support
     *
     * @param operations List of update operations to apply
     * @return List of successfully updated plugin names
     */
    private List<String> applyUpdateOperations(List<UpdateOperation> operations) {
        List<String> successful = new ArrayList<>();
        Map<String, File> backups = new HashMap<>();

        for (UpdateOperation operation : operations) {
            try {
                // Create backup for existing plugins
                if (!operation.isNewPlugin && operation.targetFile.exists()) {
                    File backup = createBackup(operation.targetFile, operation.pluginName);
                    if (backup != null) {
                        backups.put(operation.pluginName, backup);
                    }
                }

                // Perform the update
                if (copyFile(operation.updateFile, operation.targetFile)) {
                    successful.add(operation.pluginName);
                    log.info("{} plugin: {} v{}",
                            operation.isNewPlugin ? "Added" : "Updated",
                            operation.pluginName, operation.version);
                } else {
                    // Restore backup if update failed
                    restoreFromBackup(operation.pluginName, backups.get(operation.pluginName));
                }

            } catch (Exception e) {
                log.error("Failed to apply update for {}: {}", operation.pluginName, e.getMessage());
                // Restore backup if update failed
                restoreFromBackup(operation.pluginName, backups.get(operation.pluginName));
            }
        }

        // Clean up successful backups (keep failed ones for recovery)
        cleanupSuccessfulBackups(successful, backups);

        return successful;
    }

    /**
     * Finds existing plugin file by plugin name
     *
     * @param pluginName Name of the plugin to find
     * @return File object if found, null otherwise
     */
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
                // Ignore files that can't be loaded
                log.debug("Could not load metadata from {}: {}", jarFile.getName(), e.getMessage());
            }
        }
        return null;
    }

    /**
     * Creates backup of existing plugin file
     *
     * @param pluginFile Plugin file to backup
     * @param pluginName Name of the plugin
     * @return Backup file or null if backup failed
     */
    private File createBackup(File pluginFile, String pluginName) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        File backupFile = new File(backupDirectory,
                pluginName + "-" + timestamp + "-backup.jar");

        try {
            Files.copy(pluginFile.toPath(), backupFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            log.debug("Created backup: {}", backupFile.getName());
            return backupFile;
        } catch (IOException e) {
            log.error("Failed to create backup for {}: {}", pluginName, e.getMessage());
            return null;
        }
    }

    /**
     * Restores plugin from backup
     *
     * @param pluginName Name of the plugin
     * @param backupFile Backup file to restore from
     */
    private void restoreFromBackup(String pluginName, File backupFile) {
        if (backupFile == null || !backupFile.exists()) {
            log.error("No backup found for plugin: {}", pluginName);
            return;
        }

        File pluginFile = findExistingPluginFile(pluginName);
        if (pluginFile != null) {
            try {
                Files.copy(backupFile.toPath(), pluginFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                log.info("Restored plugin from backup: {}", pluginName);
            } catch (IOException e) {
                log.error("Failed to restore plugin from backup {}: {}",
                        pluginName, e.getMessage());
            }
        }
    }

    /**
     * Utility method for copying files with error handling
     *
     * @param source      Source file
     * @param destination Destination file
     * @return true if copy was successful
     */
    private boolean copyFile(File source, File destination) {
        try {
            Files.copy(source.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
            return true;
        } catch (IOException e) {
            log.error("Failed to copy file from {} to {}: {}",
                    source.getName(), destination.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * Cleans up update files after processing
     *
     * @param updateFiles Array of update files to cleanup
     */
    private void cleanupUpdateFiles(File[] updateFiles) {
        for (File updateFile : updateFiles) {
            try {
                if (!updateFile.delete()) {
                    log.warn("Failed to delete update file: {}", updateFile.getName());
                }
            } catch (Exception e) {
                log.warn("Error deleting update file {}: {}", updateFile.getName(), e.getMessage());
            }
        }
    }

    /**
     * Cleans up backup files for successful updates
     *
     * @param successful List of successfully updated plugin names
     * @param backups Map of plugin names to backup files
     */
    private void cleanupSuccessfulBackups(List<String> successful, Map<String, File> backups) {
        for (String pluginName : successful) {
            File backup = backups.get(pluginName);
            if (backup != null && backup.exists()) {
                if (backup.delete()) {
                    log.debug("Cleaned up backup for successful update: {}", pluginName);
                } else {
                    log.debug("Failed to cleanup backup for: {}", pluginName);
                }
            }
        }
    }

    /**
     * Simple version comparison (assumes semantic versioning)
     *
     * @param version1 First version
     * @param version2 Second version
     * @return true if version1 is newer than version2
     */
    private boolean isVersionNewer(String version1, String version2) {
        try {
            // Simple comparison - you might want to use a proper version comparison library
            String[] v1Parts = version1.split("\\.");
            String[] v2Parts = version2.split("\\.");

            int maxLength = Math.max(v1Parts.length, v2Parts.length);

            for (int i = 0; i < maxLength; i++) {
                int v1Part = i < v1Parts.length ? Integer.parseInt(v1Parts[i]) : 0;
                int v2Part = i < v2Parts.length ? Integer.parseInt(v2Parts[i]) : 0;

                if (v1Part > v2Part) return true;
                if (v1Part < v2Part) return false;
            }

            return false; // versions are equal
        } catch (NumberFormatException e) {
            // If version parsing fails, assume it's newer
            log.warn("Could not parse versions for comparison: {} vs {}", version1, version2);
            return true;
        }
    }

    /**
     * Ensures required directories exist
     */
    private void ensureDirectoriesExist() {
        try {
            Files.createDirectories(pluginDirectory.toPath());
            Files.createDirectories(updateDirectory.toPath());
            Files.createDirectories(backupDirectory.toPath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create required directories", e);
        }
    }

    /**
     * Shuts down the update manager
     */
    public void shutdown() {
        updateExecutor.shutdown();
        log.debug("PluginUpdateManager shut down");
    }

    /**
     * Internal class representing an update operation
     */
    private static class UpdateOperation {
        final String pluginName;
        final String version;
        final File updateFile;
        final File targetFile;
        final boolean isNewPlugin;

        UpdateOperation(String pluginName, String version, File updateFile,
                        File targetFile, boolean isNewPlugin) {
            this.pluginName = pluginName;
            this.version = version;
            this.updateFile = updateFile;
            this.targetFile = targetFile;
            this.isNewPlugin = isNewPlugin;
        }
    }

    /**
     * Result of update operation
     */
    @Getter
    public static class UpdateResult {
        private final List<String> updatedPlugins;
        private final List<String> failedUpdates;

        public UpdateResult(List<String> updatedPlugins, List<String> failedUpdates) {
            this.updatedPlugins = Collections.unmodifiableList(updatedPlugins);
            this.failedUpdates = Collections.unmodifiableList(failedUpdates);
        }

        public boolean hasUpdates() {
            return !updatedPlugins.isEmpty();
        }

        public boolean hasFailures() {
            return !failedUpdates.isEmpty();
        }

        public boolean isSuccessful() {
            return hasUpdates() && !hasFailures();
        }

        public static UpdateResult noUpdates() {
            return new UpdateResult(Collections.emptyList(), Collections.emptyList());
        }

        @Override
        public String toString() {
            return String.format("UpdateResult{updated=%d, failed=%d}",
                    updatedPlugins.size(), failedUpdates.size());
        }
    }
}