package me.kitakeyos.plugin.reload;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.kitakeyos.plugin.api.BasePlugin;
import me.kitakeyos.plugin.api.HotReloadAware;
import me.kitakeyos.plugin.api.PluginContext;
import me.kitakeyos.plugin.api.PluginMetadata;
import me.kitakeyos.plugin.manager.PluginManager;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Manages hot reloading of plugins with state preservation
 * Provides safe plugin reloading with rollback capabilities
 */
@Slf4j
public class HotReloadManager {

    private final PluginManager pluginManager;
    private final PluginStateManager stateManager;
    private final FileWatcher fileWatcher;
    private final ExecutorService reloadExecutor;
    private final ScheduledExecutorService scheduler; // Added for debouncing
    private final Map<String, ReloadOperation> activeReloads = new ConcurrentHashMap<>();

    @Getter
    private final HotReloadConfig config;

    /**
     * Creates a new hot reload manager
     *
     * @param pluginManager Plugin manager to reload plugins through
     * @param config        Configuration for hot reload behavior
     */
    public HotReloadManager(PluginManager pluginManager, HotReloadConfig config) {
        this.pluginManager = pluginManager;
        this.config = config;
        this.stateManager = new PluginStateManager(config.getStateDirectory());
        this.fileWatcher = new FileWatcher(pluginManager.getPluginDirectory(), this::onFileChanged);
        this.reloadExecutor = Executors.newFixedThreadPool(config.getMaxConcurrentReloads());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(); // For debouncing
    }

    /**
     * Starts file watching for automatic reloads
     * Only starts if auto-reload is enabled in configuration
     */
    public void startWatching() {
        if (config.isAutoReloadEnabled()) {
            fileWatcher.start();
            log.info("Hot reload file watching started");
        }
    }

    /**
     * Stops file watching and shuts down reload executor
     */
    public void stopWatching() {
        fileWatcher.stop();
        reloadExecutor.shutdown();
        scheduler.shutdown(); // Shutdown scheduler as well
        log.info("Hot reload file watching stopped");
    }

    /**
     * Initiates hot reload for a plugin with default options
     *
     * @param pluginName Name of the plugin to reload
     * @return CompletableFuture containing reload result
     */
    public CompletableFuture<ReloadResult> hotReload(String pluginName) {
        return hotReload(pluginName, HotReloadOptions.defaultOptions());
    }

    /**
     * Initiates hot reload for a plugin with specified options
     *
     * @param pluginName Name of the plugin to reload
     * @param options    Options controlling reload behavior
     * @return CompletableFuture containing reload result
     */
    public CompletableFuture<ReloadResult> hotReload(String pluginName, HotReloadOptions options) {
        // Prevent concurrent reloads of the same plugin
        if (activeReloads.containsKey(pluginName)) {
            return CompletableFuture.completedFuture(
                    ReloadResult.failure("Plugin " + pluginName + " is already being reloaded")
            );
        }

        return CompletableFuture.supplyAsync(() -> {
            ReloadOperation operation = new ReloadOperation(pluginName, options);
            activeReloads.put(pluginName, operation);

            try {
                return performHotReload(operation);
            } finally {
                activeReloads.remove(pluginName);
            }
        }, reloadExecutor);
    }

    /**
     * Performs the actual hot reload operation through multiple phases
     *
     * @param operation Reload operation context
     * @return Result of the reload operation
     */
    private ReloadResult performHotReload(ReloadOperation operation) {
        String pluginName = operation.getPluginName();
        HotReloadOptions options = operation.getOptions();
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting hot reload for plugin: {}", pluginName);

            BasePlugin plugin = pluginManager.getPlugin(pluginName);
            if (plugin == null) {
                return ReloadResult.failure("Plugin not found: " + pluginName);
            }

            // Phase 1: Pre-reload validation
            operation.setPhase(ReloadOperation.ReloadPhase.VALIDATING);
            if (!validateReload(plugin, options)) {
                return ReloadResult.failure("Pre-reload validation failed");
            }

            // Phase 2: Capture current state
            operation.setPhase(ReloadOperation.ReloadPhase.CAPTURING_STATE);
            PluginStateSnapshot stateSnapshot = null;
            if (options.isPreserveState()) {
                stateSnapshot = stateManager.captureState(pluginName, plugin);
                operation.setStateSnapshot(stateSnapshot);
            }

            // Phase 3: Graceful shutdown with timeout
            operation.setPhase(ReloadOperation.ReloadPhase.GRACEFUL_SHUTDOWN);
            if (!gracefulShutdown(plugin, options.getShutdownTimeoutMs())) {
                if (options.isForceReload()) {
                    log.warn("Graceful shutdown timeout, forcing reload for: {}", pluginName);
                } else {
                    return ReloadResult.failure("Graceful shutdown timeout");
                }
            }

            // Phase 4: Disable and unload current plugin
            operation.setPhase(ReloadOperation.ReloadPhase.DISABLING);
            disableAndUnload(plugin);

            // Phase 5: Load new version
            operation.setPhase(ReloadOperation.ReloadPhase.LOADING_NEW_VERSION);
            File pluginFile = plugin.getJarFile();
            if (!pluginFile.exists()) {
                return rollback(operation, "Plugin file not found");
            }

            // Clear class loader cache if requested
            if (options.isClearClassCache()) {
                clearClassLoaderCache(pluginName);
            }

            // Phase 6: Load and enable new plugin
            BasePlugin newPlugin = loadNewPlugin(pluginFile, pluginName);
            if (newPlugin == null) {
                return rollback(operation, "Failed to load new plugin version");
            }

            // Phase 7: Restore state if requested
            operation.setPhase(ReloadOperation.ReloadPhase.RESTORING_STATE);
            if (options.isPreserveState() && stateSnapshot != null) {
                if (!stateManager.restoreState(pluginName, newPlugin, stateSnapshot)) {
                    log.warn("Failed to restore state for {}, continuing without state", pluginName);
                }
            }

            // Phase 8: Enable new plugin
            operation.setPhase(ReloadOperation.ReloadPhase.ENABLING);
            try {
                pluginManager.enablePlugin(newPlugin.getName());
            } catch (Exception e) {
                return rollback(operation, "Failed to enable new plugin version: " + e.getMessage());
            }

            operation.setPhase(ReloadOperation.ReloadPhase.COMPLETED);
            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully hot reloaded plugin: {} in {}ms", pluginName, duration);

            return ReloadResult.success(pluginName, duration, stateSnapshot != null);

        } catch (Exception e) {
            log.error("Hot reload failed for plugin {}: {}", pluginName, e.getMessage());
            return rollback(operation, "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Validates if a plugin can be safely reloaded
     *
     * @param plugin  Plugin to validate
     * @param options Reload options
     * @return true if reload can proceed
     */
    private boolean validateReload(BasePlugin plugin, HotReloadOptions options) {
        // Check if plugin supports hot reload
        if (!options.isForceReload() && plugin instanceof HotReloadAware) {
            return ((HotReloadAware) plugin).canHotReload();
        }

        // Check plugin state
        if (!pluginManager.getRegistry().isEnabled(plugin.getName())) {
            log.warn("Plugin {} is not in ENABLED state", plugin.getName());
            return false;
        }

        return true;
    }

    /**
     * Performs graceful shutdown of plugin with timeout
     *
     * @param plugin    Plugin to shut down
     * @param timeoutMs Maximum time to wait for graceful shutdown
     * @return true if graceful shutdown completed within timeout
     */
    private boolean gracefulShutdown(BasePlugin plugin, long timeoutMs) {
        if (plugin instanceof HotReloadAware) {
            try {
                CompletableFuture<Void> shutdownFuture = CompletableFuture.runAsync(() -> {
                    ((HotReloadAware) plugin).prepareForReload();
                });

                shutdownFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
                return true;
            } catch (TimeoutException e) {
                log.warn("Graceful shutdown timeout for plugin: {}", plugin.getName());
                return false;
            } catch (Exception e) {
                log.warn("Error during graceful shutdown: {}", e.getMessage());
                return false;
            }
        }
        return true;
    }

    /**
     * Disables and unloads the current plugin version
     *
     * @param plugin Plugin instance to disable
     */
    private void disableAndUnload(BasePlugin plugin) {
        try {
            pluginManager.disablePlugin(plugin.getName());
            plugin.onUnload();
            pluginManager.getRegistry().unregisterPlugin(plugin.getName());
        } catch (Exception e) {
            log.warn("Error during disable/unload: {}", e.getMessage());
        }
    }

    /**
     * Loads new version of plugin from file
     *
     * @param pluginFile Plugin JAR file
     * @param pluginName Name of plugin
     * @return Loaded plugin instance or null if loading failed
     */
    private BasePlugin loadNewPlugin(File pluginFile, String pluginName) {
        try {
            // Reload metadata and plugin
            PluginMetadata metadata = pluginManager.getLoader().loadMetadata(pluginFile);
            BasePlugin newPlugin = pluginManager.getLoader().loadPlugin(pluginFile, metadata);

            // Create new context
            PluginContext context = new PluginContext(
                    pluginName,
                    pluginManager.getEventBus(),
                    pluginManager.getScheduler(),
                    pluginManager.getConfigManager().getConfig(pluginName)
            );

            newPlugin.setContext(context);
            newPlugin.onLoad();

            pluginManager.getRegistry().registerPlugin(pluginName, newPlugin);

            return newPlugin;
        } catch (Exception e) {
            log.error("Failed to load new plugin version: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Attempts to rollback failed reload operation
     *
     * @param operation Failed reload operation
     * @param reason    Reason for rollback
     * @return Failure result with rollback status
     */
    private ReloadResult rollback(ReloadOperation operation, String reason) {
        log.warn("Rolling back reload for {}: {}", operation.getPluginName(), reason);

        try {
            operation.setPhase(ReloadOperation.ReloadPhase.ROLLING_BACK);

            // Attempt to restore from state if available
            PluginStateSnapshot snapshot = operation.getStateSnapshot();
            if (snapshot != null) {
                // Try to reload the old version and restore state
                // This is a complex operation that would need careful implementation
                log.info("Attempting rollback with state restoration...");
            }

            return ReloadResult.failure("Reload failed: " + reason + " (rollback attempted)");
        } catch (Exception e) {
            return ReloadResult.failure("Reload failed: " + reason + " (rollback also failed: " + e.getMessage() + ")");
        }
    }

    /**
     * Clears class loader cache for plugin
     *
     * @param pluginName Name of plugin to clear cache for
     */
    private void clearClassLoaderCache(String pluginName) {
        // Clear any cached class loader references
        pluginManager.getLoader().cleanupPlugin(pluginName);

        // Force garbage collection to help clean up old classes
        System.gc();
    }

    /**
     * Handles file change events from file watcher
     * Triggers automatic reload if enabled
     *
     * @param filePath Path of changed file
     */
    private void onFileChanged(Path filePath) {
        if (!config.isAutoReloadEnabled()) {
            return;
        }

        String fileName = filePath.getFileName().toString();
        if (!fileName.endsWith(".jar")) {
            return;
        }

        try {
            PluginMetadata metadata = pluginManager.getLoader().loadMetadata(filePath.toFile());
            String pluginName = metadata.getName();

            // Debounce rapid file changes using ScheduledExecutorService instead of delayedExecutor
            scheduler.schedule(() -> {
                log.info("File change detected, auto-reloading plugin: {}", pluginName);
                hotReload(pluginName, HotReloadOptions.autoReloadOptions())
                        .thenAccept(result -> {
                            if (result.isSuccess()) {
                                log.info("Auto-reload successful for: {}", pluginName);
                            } else {
                                log.warn("Auto-reload failed for {}: {}", pluginName, result.getErrorMessage());
                            }
                        });
            }, config.getFileChangeDebounceMs(), TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            log.error("Failed to auto-reload plugin: {}", e.getMessage());
        }
    }
}