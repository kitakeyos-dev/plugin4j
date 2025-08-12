package me.kitakeyos.plugin.reload;

import lombok.Getter;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Immutable snapshot of plugin state for hot reload operations
 * Contains all necessary data to restore plugin state after reload
 */
@Getter
public class PluginStateSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String pluginName;
    private final String version;
    private final long timestamp;
    private final Map<String, Object> configData;
    private final Map<String, Object> customData;
    private final Set<Long> activeTaskIds;
    private final Map<String, Object> eventListenerStates;

    /**
     * Creates a new plugin state snapshot
     *
     * @param pluginName          Name of the plugin
     * @param version             Plugin version
     * @param configData          Plugin configuration data
     * @param customData          Custom plugin state data
     * @param activeTaskIds       Set of active scheduled task IDs
     * @param eventListenerStates Event listener states
     */
    public PluginStateSnapshot(String pluginName, String version,
                               Map<String, Object> configData,
                               Map<String, Object> customData,
                               Set<Long> activeTaskIds,
                               Map<String, Object> eventListenerStates) {
        this.pluginName = pluginName;
        this.version = version;
        this.timestamp = System.currentTimeMillis();
        // Create defensive copies to ensure immutability
        this.configData = new ConcurrentHashMap<>(configData);
        this.customData = new ConcurrentHashMap<>(customData);
        this.activeTaskIds = new CopyOnWriteArraySet<>(activeTaskIds);
        this.eventListenerStates = new ConcurrentHashMap<>(eventListenerStates);
    }

    /**
     * Checks if this snapshot is compatible with a plugin version
     * Uses semantic versioning rules for compatibility checking
     *
     * @param newVersion Version to check compatibility with
     * @return true if versions are compatible for state restoration
     */
    public boolean isCompatibleWith(String newVersion) {
        // Exact version match is always compatible
        return version.equals(newVersion) || isMinorVersionUpdate(version, newVersion);
    }

    /**
     * Checks if version change is a minor update (compatible for state restoration)
     *
     * @param oldVersion Previous plugin version
     * @param newVersion New plugin version
     * @return true if update is minor and compatible
     */
    private boolean isMinorVersionUpdate(String oldVersion, String newVersion) {
        try {
            String[] oldParts = oldVersion.split("\\.");
            String[] newParts = newVersion.split("\\.");

            if (oldParts.length >= 2 && newParts.length >= 2) {
                // Major version must be same, minor version can be newer
                return oldParts[0].equals(newParts[0]) && // Major version same
                        Integer.parseInt(newParts[1]) >= Integer.parseInt(oldParts[1]); // Minor version >= old
            }
        } catch (NumberFormatException e) {
            // Fallback to exact match if version parsing fails
        }
        return oldVersion.equals(newVersion);
    }
}