package me.kitakeyos.plugin.api;

import java.util.Map;
import java.util.Set;

public interface StatefulPlugin {
    /**
     * Save the current state of the plugin
     */
    Map<String, Object> saveState();

    /**
     * Load previously saved state
     */
    void loadState(Map<String, Object> state);

    /**
     * Restore scheduled tasks based on saved task IDs
     */
    default void restoreScheduledTasks(Set<Long> taskIds) {
        // Default implementation - plugins can override
    }
}
