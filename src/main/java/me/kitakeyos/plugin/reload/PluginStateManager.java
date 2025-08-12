package me.kitakeyos.plugin.reload;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import me.kitakeyos.plugin.api.BasePlugin;
import me.kitakeyos.plugin.api.StatefulPlugin;
import me.kitakeyos.plugin.config.PluginConfig;
import me.kitakeyos.plugin.exceptions.StateException;
import me.kitakeyos.plugin.scheduler.TaskScheduler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages plugin state capture and restoration during hot reloads
 * Handles serialization of plugin configuration and custom state data
 */
@Slf4j
public class PluginStateManager {

    private final Path stateDirectory;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new state manager with specified state directory
     *
     * @param stateDirectory Directory to store state snapshots
     * @throws RuntimeException if state directory cannot be created
     */
    public PluginStateManager(Path stateDirectory) {
        this.stateDirectory = stateDirectory;
        this.objectMapper = new ObjectMapper();

        try {
            Files.createDirectories(stateDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create state directory", e);
        }
    }

    /**
     * Captures complete state of a plugin for later restoration
     *
     * @param pluginName Name of the plugin
     * @param plugin     Plugin instance to capture state from
     * @return State snapshot containing all captured data
     * @throws StateException if state capture fails
     */
    public PluginStateSnapshot captureState(String pluginName, BasePlugin plugin) {
        try {
            log.info("Capturing state for plugin: {}", pluginName);

            // Capture plugin configuration data
            Map<String, Object> configData = new ConcurrentHashMap<>();
            if (plugin.getContext() != null && plugin.getContext().getConfig() != null) {
                for (String key : plugin.getContext().getConfig().getKeys()) {
                    configData.put(key, plugin.getContext().getConfig().getString(key));
                }
            }

            // Capture custom plugin state if plugin implements StatefulPlugin
            Map<String, Object> customData = new ConcurrentHashMap<>();
            if (plugin instanceof StatefulPlugin) {
                StatefulPlugin statefulPlugin = (StatefulPlugin) plugin;
                customData = statefulPlugin.saveState();
            }

            // Capture active scheduled task IDs
            Set<Long> activeTaskIds = plugin.getContext() != null ? getActiveTaskIds(plugin.getContext().getScheduler()) : new HashSet<>();

            // Capture event listener states (placeholder for future implementation)
            Map<String, Object> eventListenerStates = new ConcurrentHashMap<>();

            PluginStateSnapshot snapshot = new PluginStateSnapshot(
                    pluginName, plugin.getVersion(), configData, customData,
                    activeTaskIds, eventListenerStates
            );

            // Persist snapshot to disk for durability
            persistState(pluginName, snapshot);

            log.info("Successfully captured state for plugin: {}", pluginName);
            return snapshot;

        } catch (Exception e) {
            log.error("Failed to capture state for plugin {}: {}", pluginName, e.getMessage());
            throw new StateException("Failed to capture plugin state", e);
        }
    }

    /**
     * Restores plugin state from a snapshot
     *
     * @param pluginName Name of the plugin
     * @param plugin     Plugin instance to restore state to
     * @param snapshot   State snapshot to restore from
     * @return true if state restoration was successful
     */
    public boolean restoreState(String pluginName, BasePlugin plugin, PluginStateSnapshot snapshot) {
        try {
            log.info("Restoring state for plugin: {}", pluginName);

            // Check version compatibility before attempting restore
            if (!snapshot.isCompatibleWith(plugin.getVersion())) {
                log.warn("Version incompatible, skipping state restore for {}", pluginName);
                return false;
            }

            // Restore plugin configuration
            if (plugin.getContext() != null && plugin.getContext().getConfig() != null) {
                restoreConfig(plugin.getContext().getConfig(), snapshot.getConfigData());
            }

            // Restore custom plugin state if supported
            if (plugin instanceof StatefulPlugin && !snapshot.getCustomData().isEmpty()) {
                StatefulPlugin statefulPlugin = (StatefulPlugin) plugin;
                statefulPlugin.loadState(snapshot.getCustomData());
            }

            // Restore scheduled tasks (recreate based on saved task IDs)
            if (plugin.getContext() != null) {
                restoreScheduledTasks(plugin, snapshot.getActiveTaskIds());
            }

            log.info("Successfully restored state for plugin: {}", pluginName);
            return true;

        } catch (Exception e) {
            log.error("Failed to restore state for plugin {}: {}", pluginName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Persists state snapshot to disk with atomic write
     *
     * @param pluginName Name of the plugin
     * @param snapshot   State snapshot to persist
     * @throws IOException if persistence fails
     */
    private void persistState(String pluginName, PluginStateSnapshot snapshot) throws IOException {
        Path stateFile = getStateFile(pluginName);
        Path tempFile = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");

        // Write to temp file first for atomic operation
        objectMapper.writeValue(tempFile.toFile(), snapshot);

        // Atomic move to final location
        Files.move(tempFile, stateFile, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Gets the file path for storing plugin state
     *
     * @param pluginName Name of the plugin
     * @return Path to state file
     */
    private Path getStateFile(String pluginName) {
        return stateDirectory.resolve(pluginName + ".state");
    }

    /**
     * Restores plugin configuration from captured data
     *
     * @param config     Plugin configuration object
     * @param configData Captured configuration data
     */
    private void restoreConfig(PluginConfig config, Map<String, Object> configData) {
        for (Map.Entry<String, Object> entry : configData.entrySet()) {
            config.set(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Gets active task IDs from scheduler
     *
     * @param scheduler Task scheduler instance
     * @return Set of active task IDs
     */
    private Set<Long> getActiveTaskIds(TaskScheduler scheduler) {
        // This would need to be implemented in TaskScheduler to expose active task IDs
        return new HashSet<>(); // Placeholder implementation
    }

    /**
     * Restores scheduled tasks for a plugin
     *
     * @param plugin  Plugin instance
     * @param taskIds Set of task IDs to restore
     */
    private void restoreScheduledTasks(BasePlugin plugin, Set<Long> taskIds) {
        // Plugin-specific logic to recreate tasks based on saved IDs
        // This could involve calling plugin methods to recreate tasks
        if (plugin instanceof StatefulPlugin) {
            StatefulPlugin statefulPlugin = (StatefulPlugin) plugin;
            statefulPlugin.restoreScheduledTasks(taskIds);
        }
    }
}