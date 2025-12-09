package me.kitakeyos.plugin.manager;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.kitakeyos.plugin.api.BasePlugin;
import me.kitakeyos.plugin.api.PluginContext;
import me.kitakeyos.plugin.api.PluginMetadata;
import me.kitakeyos.plugin.config.ConfigManager;
import me.kitakeyos.plugin.events.EventBus;
import me.kitakeyos.plugin.exceptions.PluginException;
import me.kitakeyos.plugin.exceptions.PluginNotFoundException;
import me.kitakeyos.plugin.exceptions.PluginOperationException;
import me.kitakeyos.plugin.exceptions.PluginOperationException.PluginOperation;
import me.kitakeyos.plugin.exceptions.PluginStateException;
import me.kitakeyos.plugin.scheduler.TaskScheduler;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simplified Plugin Manager focused on core lifecycle management
 */
@Slf4j
public class PluginManager {

    @Getter
    private final PluginRegistry registry;
    @Getter
    private final EventBus eventBus;
    @Getter
    private final ConfigManager configManager;
    @Getter
    private final TaskScheduler scheduler;
    @Getter
    private final File pluginDirectory;

    // Specialized managers
    @Getter
    private final PluginLoader loader;
    private final DependencyResolver dependencyResolver;
    @Getter
    private final ExtensionManager extensionManager;

    // Plugin metadata cache
    private final Map<String, PluginMetadata> pluginMetadata = new ConcurrentHashMap<>();

    /**
     * Initializes plugin manager
     *
     * @param pluginDirectory Directory containing plugin JAR files
     * @param eventBus        Event bus for plugin communication
     */
    public PluginManager(File pluginDirectory, EventBus eventBus) {
        this.pluginDirectory = validatePluginDirectory(pluginDirectory);
        this.eventBus = Objects.requireNonNull(eventBus, "Event bus cannot be null");

        // Initialize core managers
        this.registry = new PluginRegistry();
        this.loader = new PluginLoader();
        this.dependencyResolver = new DependencyResolver();
        this.extensionManager = new ExtensionManager();

        // Initialize services
        File configDirectory = new File(pluginDirectory.getParent(), "plugin-data");
        this.configManager = new ConfigManager(configDirectory);
        this.scheduler = new TaskScheduler();

        // Configure manager relationships
        this.loader.setExtensionManager(this.extensionManager);

        // Ensure directories exist
        ensureDirectoriesExist(pluginDirectory);

        log.info("PluginManager initialized with plugin directory: {}", pluginDirectory.getAbsolutePath());
    }

    /**
     * Alternative constructor without event bus for minimal setups
     */
    public PluginManager(File pluginDirectory) {
        this(pluginDirectory, null);
    }

    /**
     * Validates and returns the plugin directory
     */
    private File validatePluginDirectory(File pluginDirectory) {
        return Objects.requireNonNull(pluginDirectory, "Plugin directory cannot be null");
    }

    // =================== CORE PLUGIN LIFECYCLE ===================

    /**
     * Loads all plugins from plugin directory with dependency resolution
     */
    public void loadAllPlugins() {
        log.info("Starting plugin loading process...");

        try {
            // Discover and validate plugins
            Map<String, File> pluginFiles = discoverPlugins();
            if (pluginFiles.isEmpty()) {
                log.info("No plugins found to load");
                return;
            }

            // Resolve dependencies and determine loading order
            List<String> loadOrder = dependencyResolver.resolveDependencies(pluginMetadata);
            log.info("Plugin loading order resolved: {}", loadOrder);

            // Load plugins in dependency order
            int loadedCount = 0;
            for (String pluginName : loadOrder) {
                try {
                    File pluginFile = pluginFiles.get(pluginName);
                    if (pluginFile != null && loadSinglePlugin(pluginName, pluginFile)) {
                        loadedCount++;
                    }
                } catch (PluginStateException e) {
                    // State transition error - convert to operation exception
                    log.error("Invalid state transition for plugin {}: {}", pluginName, e.getMessage());
                    throw new PluginOperationException(
                            PluginOperation.ENABLE, pluginName, e);
                } catch (Exception e) {
                    log.error("Failed to load plugin: {} - {}", pluginName, e.getMessage(), e);
                }
            }

            log.info("Plugin loading completed: {}/{} plugins loaded, {} extension points registered",
                    loadedCount, pluginFiles.size(), extensionManager.getExtensionPointsInfo().size());

        } catch (Exception e) {
            throw new PluginException("Failed to load plugins", e);
        }
    }

    /**
     * Loads a single plugin with full lifecycle setup
     */
    private boolean loadSinglePlugin(String pluginName, File pluginFile) {
        try {
            log.debug("Loading plugin: {}", pluginName);

            // Load plugin metadata if not already cached
            PluginMetadata metadata = pluginMetadata.get(pluginName);
            if (metadata == null) {
                metadata = loader.loadMetadata(pluginFile);
                pluginMetadata.put(pluginName, metadata);
            }

            // Load plugin instance
            BasePlugin plugin = loader.loadPlugin(pluginFile, metadata);

            // Create plugin context with required services
            PluginContext context = new PluginContext(
                    pluginName,
                    eventBus,
                    scheduler,
                    configManager.getConfig(pluginName));

            // Initialize plugin
            plugin.setContext(context);
            plugin.onLoad();

            // Register with registry
            registry.registerPlugin(pluginName, plugin);

            log.info("Successfully loaded plugin: {} v{}", pluginName, metadata.getVersion());
            return true;

        } catch (Exception e) {
            log.error("Failed to load plugin {}: {}", pluginName, e.getMessage());
            return false;
        }
    }

    /**
     * Loads a specific plugin by name from the plugin directory
     *
     * @param pluginName Name of the plugin to load
     * @return true if loaded successfully
     * @throws PluginNotFoundException  if plugin file is not found
     * @throws PluginOperationException if loading fails
     */
    public boolean loadPlugin(String pluginName) {
        log.info("Loading specific plugin: {}", pluginName);

        // Find plugin file
        File pluginFile = findPluginFile(pluginName);
        if (pluginFile == null) {
            throw new PluginNotFoundException("Plugin file not found: " + pluginName);
        }

        try {
            return loadSinglePlugin(pluginName, pluginFile);
        } catch (Exception e) {
            throw new PluginOperationException(PluginOperation.ENABLE, pluginName, e);
        }
    }

    /**
     * Enables all loaded plugins
     */
    public void enableAllPlugins() {
        log.info("Enabling all loaded plugins...");

        Collection<BasePlugin> plugins = registry.getAllPlugins();
        int enabledCount = 0;

        for (BasePlugin plugin : plugins) {
            try {
                if (!registry.isEnabled(plugin.getName())) {
                    enablePlugin(plugin.getName());
                    enabledCount++;
                }
            } catch (Exception e) {
                log.error("Failed to enable plugin during bulk enable: {}", plugin.getName(), e);
            }
        }

        log.info("Enabled {}/{} plugins", enabledCount, plugins.size());
    }

    /**
     * Disables all enabled plugins
     */
    public void disableAllPlugins() {
        log.info("Disabling all enabled plugins...");

        Collection<BasePlugin> plugins = registry.getAllPlugins();
        int disabledCount = 0;

        for (BasePlugin plugin : plugins) {
            try {
                if (registry.isEnabled(plugin.getName())) {
                    disablePlugin(plugin.getName());
                    disabledCount++;
                }
            } catch (Exception e) {
                log.error("Failed to disable plugin during bulk disable: {}", plugin.getName(), e);
            }
        }

        log.info("Disabled {} plugins", disabledCount);
    }

    /**
     * Enables a specific plugin by name
     */
    public void enablePlugin(String pluginName) {
        BasePlugin plugin = getPluginOrThrow(pluginName);

        if (registry.isEnabled(pluginName)) {
            log.debug("Plugin {} is already enabled", pluginName);
            return;
        }

        try {
            log.debug("Enabling plugin: {}", pluginName);

            // Register with event bus if available
            if (eventBus != null) {
                eventBus.register(plugin);
            } else {
                log.debug("Skipping event bus registration - event bus not available");
            }

            // Call plugin enable hook
            plugin.onEnable();

            // Update state
            registry.setPluginState(pluginName, PluginState.ENABLED);

            log.info("Successfully enabled plugin: {}", pluginName);

        } catch (Exception e) {
            // Set error state and cleanup
            registry.setPluginState(pluginName, PluginState.ERROR);
            try {
                if (eventBus != null) {
                    eventBus.unregister(plugin);
                }
            } catch (Exception cleanupException) {
                log.warn("Failed to cleanup after enable failure for {}: {}",
                        pluginName, cleanupException.getMessage());
            }

            throw new PluginOperationException(PluginOperation.ENABLE, pluginName, e);
        }
    }

    /**
     * Disables a specific plugin by name
     */
    public void disablePlugin(String pluginName) {
        BasePlugin plugin = getPluginOrThrow(pluginName);

        if (registry.isDisabled(pluginName)) {
            log.debug("Plugin {} is already disabled", pluginName);
            return;
        }

        try {
            log.debug("Disabling plugin: {}", pluginName);

            // Unregister from event bus if available
            if (eventBus != null) {
                eventBus.unregister(plugin);
            } else {
                log.debug("Skipping event bus unregistration - event bus not available");
            }

            // Call plugin disable hook
            plugin.onDisable();

            // Cleanup extensions for this plugin
            extensionManager.unregisterPluginExtensions(pluginName);

            // Update state
            registry.setPluginState(pluginName, PluginState.DISABLED);

            log.info("Successfully disabled plugin: {}", pluginName);

        } catch (Exception e) {
            // Set error state but don't re-register with event bus
            registry.setPluginState(pluginName, PluginState.ERROR);

            throw new PluginOperationException(PluginOperation.DISABLE, pluginName, e);
        }
    }

    /**
     * Reloads a specific plugin (disable, unload, load, enable)
     */
    public void reloadPlugin(String pluginName) {
        log.info("Reloading plugin: {}", pluginName);

        BasePlugin plugin = getPluginOrThrow(pluginName);
        File pluginFile = plugin.getJarFile();

        if (pluginFile == null || !pluginFile.exists()) {
            throw new PluginOperationException(
                    PluginOperation.RELOAD,
                    pluginName,
                    "Plugin JAR file not found or not accessible");
        }

        boolean wasEnabled = registry.isEnabled(pluginName);

        try {
            // Disable if enabled
            if (wasEnabled) {
                disablePlugin(pluginName);
            }

            // Unload current plugin
            unloadPlugin(pluginName);

            // Reload metadata
            pluginMetadata.remove(pluginName);

            // Load new instance
            if (loadSinglePlugin(pluginName, pluginFile)) {
                // Re-enable if it was enabled before
                if (wasEnabled) {
                    enablePlugin(pluginName);
                }
                log.info("Successfully reloaded plugin: {}", pluginName);
            } else {
                throw new PluginOperationException(
                        PluginOperation.RELOAD,
                        pluginName,
                        "Failed to load plugin after unloading");
            }

        } catch (PluginOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new PluginOperationException(PluginOperation.RELOAD, pluginName, e);
        }
    }

    /**
     * Unloads a plugin (calls onUnload and removes from registry)
     */
    public void unloadPlugin(String pluginName) {
        BasePlugin plugin = registry.getPlugin(pluginName);
        if (plugin != null) {
            try {
                // Disable first if enabled
                if (registry.isEnabled(pluginName)) {
                    disablePlugin(pluginName);
                }

                // Call unload hook
                plugin.onUnload();

                // Clean up loader resources
                loader.cleanupPlugin(pluginName);

            } catch (Exception e) {
                log.warn("Error during plugin unload for {}: {}", pluginName, e.getMessage());
            }

            // Remove from registry
            registry.unregisterPlugin(pluginName);
            pluginMetadata.remove(pluginName);

            log.info("Unloaded plugin: {}", pluginName);
        }
    }

    // =================== PLUGIN QUERYING ===================

    /**
     * Gets a plugin instance by name
     */
    public BasePlugin getPlugin(String name) {
        return registry.getPlugin(name);
    }

    /**
     * Gets a plugin instance by name, throwing exception if not found
     */
    public BasePlugin getPluginOrThrow(String name) {
        BasePlugin plugin = registry.getPlugin(name);
        if (plugin == null) {
            throw new PluginNotFoundException(name);
        }
        return plugin;
    }

    /**
     * Gets all loaded plugins
     */
    public Collection<BasePlugin> getLoadedPlugins() {
        return registry.getAllPlugins();
    }

    /**
     * Gets metadata for a specific plugin
     */
    public PluginMetadata getPluginMetadata(String name) {
        return pluginMetadata.get(name);
    }

    /**
     * Checks if a plugin is loaded
     */
    public boolean isPluginLoaded(String name) {
        return registry.isPluginLoaded(name);
    }

    /**
     * Gets plugin names by state
     */
    public List<String> getPluginsByState(PluginState state) {
        return registry.getPluginsByState(state);
    }

    /**
     * Gets registry status summary
     */
    public PluginRegistry.RegistryStatus getRegistryStatus() {
        return registry.getStatus();
    }

    /**
     * Gets list of all available plugin files in directory
     */
    public List<String> getAvailablePlugins() {
        File[] jarFiles = pluginDirectory.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jarFiles == null) {
            return Collections.emptyList();
        }

        List<String> available = new ArrayList<>();
        for (File jarFile : jarFiles) {
            try {
                PluginMetadata metadata = loader.loadMetadata(jarFile);
                available.add(metadata.getName());
            } catch (Exception e) {
                log.debug("Failed to read metadata from {}: {}", jarFile.getName(), e.getMessage());
                // Add filename without extension as fallback
                String name = jarFile.getName().replace(".jar", "");
                available.add(name);
            }
        }

        return available;
    }

    // =================== EXTENSION POINT MANAGEMENT ===================

    /**
     * Gets all extensions for Extension Point
     */
    public <T> List<T> getExtensions(Class<T> extensionPointClass) {
        return extensionManager.getExtensions(extensionPointClass);
    }

    /**
     * Gets first extension (highest priority)
     */
    public <T> Optional<T> getExtension(Class<T> extensionPointClass) {
        return extensionManager.getExtension(extensionPointClass);
    }

    /**
     * Gets extensions by plugin
     */
    public <T> List<T> getExtensionsByPlugin(Class<T> extensionPointClass, String pluginName) {
        return extensionManager.getExtensionsByPlugin(extensionPointClass, pluginName);
    }

    /**
     * Gets information about Extension Points
     */
    public Map<String, ExtensionManager.ExtensionPointInfo> getExtensionPointsInfo() {
        return extensionManager.getExtensionPointsInfo();
    }

    /**
     * Registers Extension Point manually (if not from plugin)
     */
    public void registerExtensionPoint(Class<?> extensionPointClass) {
        extensionManager.registerExtensionPoint(extensionPointClass);
    }

    // =================== HELPER METHODS ===================

    /**
     * Discovers plugins and loads their metadata
     */
    private Map<String, File> discoverPlugins() {
        log.debug("Discovering plugins in: {}", pluginDirectory.getAbsolutePath());

        File[] jarFiles = pluginDirectory.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jarFiles == null) {
            log.warn("Plugin directory is not accessible or contains no files");
            return Collections.emptyMap();
        }

        Map<String, File> pluginFiles = new HashMap<>();

        for (File jarFile : jarFiles) {
            try {
                PluginMetadata metadata = loader.loadMetadata(jarFile);
                String pluginName = metadata.getName();

                pluginMetadata.put(pluginName, metadata);
                pluginFiles.put(pluginName, jarFile);

                log.debug("Discovered plugin: {} v{} from {}",
                        pluginName, metadata.getVersion(), jarFile.getName());

            } catch (Exception e) {
                log.warn("Failed to load metadata from {}: {}", jarFile.getName(), e.getMessage());
            }
        }

        log.info("Discovered {} valid plugins", pluginFiles.size());
        return pluginFiles;
    }

    /**
     * Finds plugin file by plugin name
     */
    private File findPluginFile(String pluginName) {
        File[] jarFiles = pluginDirectory.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jarFiles == null) {
            return null;
        }

        for (File jarFile : jarFiles) {
            try {
                PluginMetadata metadata = loader.loadMetadata(jarFile);
                if (pluginName.equals(metadata.getName())) {
                    return jarFile;
                }
            } catch (Exception e) {
                log.debug("Could not load metadata from {}: {}", jarFile.getName(), e.getMessage());
                // Try matching filename without extension
                String fileName = jarFile.getName().replace(".jar", "");
                if (pluginName.equals(fileName)) {
                    return jarFile;
                }
            }
        }
        return null;
    }

    /**
     * Ensures required directories exist
     */
    private void ensureDirectoriesExist(File... directories) {
        for (File directory : directories) {
            if (!directory.exists() && !directory.mkdirs()) {
                throw new RuntimeException("Failed to create directory: " + directory.getAbsolutePath());
            }
        }
    }

    /**
     * Checks if event bus functionality is available
     */
    public boolean isEventBusAvailable() {
        return eventBus != null;
    }

    // =================== SHUTDOWN ===================

    /**
     * Shuts down the plugin manager and all plugins
     * Gracefully disables all plugins, calls onUnload, and cleans up resources
     */
    public void shutdown() {
        log.info("Shutting down PluginManager...");

        try {
            // Disable all plugins
            disableAllPlugins();

            // Unload all plugins
            Collection<BasePlugin> plugins = getLoadedPlugins();
            for (BasePlugin plugin : plugins) {
                try {
                    unloadPlugin(plugin.getName());
                } catch (Exception e) {
                    log.error("Error unloading plugin during shutdown: {}", plugin.getName(), e);
                }
            }

            // Clear metadata
            pluginMetadata.clear();

            // Cleanup managers
            extensionManager.clearAll();
            scheduler.shutdown();
            loader.cleanupAllTempFiles();

            // Clear registry
            registry.clear();

            // Shutdown event bus if available
            if (eventBus != null) {
                eventBus.shutdown();
            }

            log.info("PluginManager shutdown completed");

        } catch (Exception e) {
            log.error("Error during PluginManager shutdown", e);
        }
    }
}