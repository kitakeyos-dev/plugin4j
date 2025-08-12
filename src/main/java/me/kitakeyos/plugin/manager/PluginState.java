package me.kitakeyos.plugin.manager;

import lombok.Getter;

/**
 * Enhanced enumeration of plugin states with additional metadata
 */
@Getter
public enum PluginState {
    /**
     * Plugin has been loaded but not yet enabled
     * Plugin class is instantiated and onLoad() has been called
     */
    LOADED("Loaded", "Plugin is loaded but not active", false),

    /**
     * Plugin is enabled and active
     * onEnable() has been called and plugin is receiving events
     */
    ENABLED("Enabled", "Plugin is active and running", true),

    /**
     * Plugin is disabled but still loaded
     * onDisable() has been called but plugin remains in memory
     */
    DISABLED("Disabled", "Plugin is loaded but inactive", false),

    /**
     * Plugin is in an error state
     * Something went wrong during loading, enabling, or runtime
     */
    ERROR("Error", "Plugin encountered an error", false);

    private final String displayName;
    private final String description;
    private final boolean active;

    PluginState(String displayName, String description, boolean active) {
        this.displayName = displayName;
        this.description = description;
        this.active = active;
    }

    /**
     * Checks if this is a terminal error state
     *
     * @return true if this is the ERROR state
     */
    public boolean isError() {
        return this == ERROR;
    }

    /**
     * Checks if plugin can be enabled from this state
     *
     * @return true if plugin can transition to ENABLED
     */
    public boolean canEnable() {
        return this == LOADED || this == DISABLED;
    }

    /**
     * Checks if plugin can be disabled from this state
     *
     * @return true if plugin can transition to DISABLED
     */
    public boolean canDisable() {
        return this == ENABLED;
    }

    /**
     * Gets the next logical state for enable operation
     *
     * @return ENABLED if can enable, otherwise throws exception
     * @throws IllegalStateException if cannot enable from current state
     */
    public PluginState getEnabledState() {
        if (!canEnable()) {
            throw new IllegalStateException("Cannot enable plugin from " + this + " state");
        }
        return ENABLED;
    }

    /**
     * Gets the next logical state for disable operation
     *
     * @return DISABLED if can disable, otherwise throws exception
     * @throws IllegalStateException if cannot disable from current state
     */
    public PluginState getDisabledState() {
        if (!canDisable()) {
            throw new IllegalStateException("Cannot disable plugin from " + this + " state");
        }
        return DISABLED;
    }

    @Override
    public String toString() {
        return displayName;
    }
}