package me.kitakeyos.plugin.manager;

import lombok.extern.slf4j.Slf4j;
import me.kitakeyos.plugin.api.BasePlugin;
import me.kitakeyos.plugin.exceptions.PluginStateException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * Clears all plugins and states
 * Should only be used during shutdown
 * Enhanced Registry for managing plugin instances and their states
 * Thread-safe storage with state validation and transition rules
 */
@Slf4j
public class PluginRegistry {
    private final Map<String, BasePlugin> plugins = new ConcurrentHashMap<>();
    private final Map<String, PluginState> pluginStates = new ConcurrentHashMap<>();

    // State transition validation rules
    private static final Map<PluginState, Set<PluginState>> VALID_TRANSITIONS;

    static {
        Map<PluginState, Set<PluginState>> transitions = new HashMap<>();
        transitions.put(PluginState.LOADED, new HashSet<>(Arrays.asList(PluginState.ENABLED, PluginState.ERROR)));
        transitions.put(PluginState.ENABLED, new HashSet<>(Arrays.asList(PluginState.DISABLED, PluginState.ERROR)));
        transitions.put(PluginState.DISABLED, new HashSet<>(Arrays.asList(PluginState.ENABLED, PluginState.ERROR)));
        transitions.put(PluginState.ERROR, new HashSet<>(Arrays.asList(PluginState.LOADED, PluginState.DISABLED, PluginState.ENABLED)));

        VALID_TRANSITIONS = Collections.unmodifiableMap(transitions);
    }

    /**
     * Checks if a plugin is currently enabled
     *
     * @param name Name of the plugin
     * @return true if plugin is in ENABLED state
     */
    public boolean isEnabled(String name) {
        PluginState state = getPluginState(name);
        return state == PluginState.ENABLED;
    }

    /**
     * Checks if a plugin is currently disabled
     *
     * @param name Name of the plugin
     * @return true if plugin is in DISABLED state
     */
    public boolean isDisabled(String name) {
        PluginState state = getPluginState(name);
        return state == PluginState.DISABLED;
    }

    /**
     * Checks if a plugin is in error state
     *
     * @param name Name of the plugin
     * @return true if plugin is in ERROR state
     */
    public boolean isInError(String name) {
        PluginState state = getPluginState(name);
        return state == PluginState.ERROR;
    }

    /**
     * Checks if a plugin is loaded (registered in the registry)
     *
     * @param name Name of the plugin
     * @return true if plugin is registered
     */
    public boolean isPluginLoaded(String name) {
        return plugins.containsKey(name);
    }

    /**
     * Registers a plugin instance with the registry
     * Sets initial state to LOADED
     *
     * @param name   Name of the plugin
     * @param plugin Plugin instance to register
     * @throws IllegalArgumentException if plugin name is null or empty
     * @throws IllegalStateException    if plugin is already registered
     */
    public void registerPlugin(String name, BasePlugin plugin) {
        validatePluginName(name);
        Objects.requireNonNull(plugin, "Plugin instance cannot be null");

        if (isPluginLoaded(name)) {
            throw new IllegalStateException("Plugin already registered: " + name);
        }

        plugins.put(name, plugin);
        pluginStates.put(name, PluginState.LOADED);
        log.debug("Registered plugin: {} in LOADED state", name);
    }

    /**
     * Unregisters a plugin from the registry
     * Removes both plugin instance and state tracking
     *
     * @param name Name of the plugin to unregister
     * @return true if plugin was removed, false if not found
     */
    public boolean unregisterPlugin(String name) {
        validatePluginName(name);

        BasePlugin removed = plugins.remove(name);
        PluginState removedState = pluginStates.remove(name);

        if (removed != null) {
            log.debug("Unregistered plugin: {} (was in {} state)", name, removedState);
            return true;
        }
        return false;
    }

    /**
     * Gets a plugin instance by name
     *
     * @param name Name of the plugin
     * @return BasePlugin instance or null if not found
     */
    public BasePlugin getPlugin(String name) {
        validatePluginName(name);
        return plugins.get(name);
    }

    /**
     * Gets all registered plugin instances
     *
     * @return Collection of all plugin instances (immutable view)
     */
    public Collection<BasePlugin> getAllPlugins() {
        return Collections.unmodifiableCollection(plugins.values());
    }

    /**
     * Gets all plugin names
     *
     * @return Set of all registered plugin names (immutable view)
     */
    public Set<String> getAllPluginNames() {
        return Collections.unmodifiableSet(plugins.keySet());
    }

    /**
     * Gets plugins by state
     *
     * @param state State to filter by
     * @return List of plugin names in the specified state
     */
    public List<String> getPluginsByState(PluginState state) {
        Objects.requireNonNull(state, "State cannot be null");

        return pluginStates.entrySet().stream()
                .filter(entry -> entry.getValue() == state)
                .map(Map.Entry::getKey)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    /**
     * Gets the current state of a plugin
     *
     * @param name Name of the plugin
     * @return PluginState, defaults to ERROR if not found
     */
    public PluginState getPluginState(String name) {
        validatePluginName(name);
        return pluginStates.getOrDefault(name, PluginState.ERROR);
    }

    /**
     * Sets the state of a plugin with validation
     *
     * @param name     Name of the plugin
     * @param newState New state to set
     * @throws IllegalArgumentException if plugin name is invalid or state is null
     * @throws IllegalStateException    if state transition is invalid
     */
    public void setPluginState(String name, PluginState newState) {
        validatePluginName(name);
        Objects.requireNonNull(newState, "Plugin state cannot be null");

        if (!isPluginLoaded(name)) {
            throw new IllegalStateException("Cannot set state for unregistered plugin: " + name);
        }

        PluginState currentState = getPluginState(name);

        // Allow any transition if current state is ERROR, or validate transition
        if (currentState != PluginState.ERROR && !isValidTransition(currentState, newState)) {
            throw new IllegalStateException(
                    String.format("Invalid state transition for plugin '%s': %s -> %s",
                            name, currentState, newState));
        }

        pluginStates.put(name, newState);
        log.debug("Plugin '{}' state changed: {} -> {}", name, currentState, newState);
    }

    /**
     * Forces plugin state without validation (use with caution)
     * Useful for recovery scenarios
     *
     * @param name  Name of the plugin
     * @param state State to force
     */
    public void forcePluginState(String name, PluginState state) {
        validatePluginName(name);
        Objects.requireNonNull(state, "Plugin state cannot be null");

        PluginState oldState = pluginStates.put(name, state);
        log.warn("Forced plugin '{}' state: {} -> {} (validation bypassed)",
                name, oldState, state);
    }

    /**
     * Gets the total number of registered plugins
     *
     * @return Number of plugins in the registry
     */
    public int getPluginCount() {
        return plugins.size();
    }

    /**
     * Gets count of plugins by state
     *
     * @return Map of state to count
     */
    public Map<PluginState, Long> getPluginCountByState() {
        return pluginStates.values().stream()
                .collect(Collectors.groupingBy(
                        Function.identity(),
                        HashMap::new,
                        Collectors.counting()
                ));
    }

    /**
     * Gets registry status summary
     *
     * @return RegistryStatus with counts and state information
     */
    public RegistryStatus getStatus() {
        Map<PluginState, Long> stateCounts = getPluginCountByState();

        return new RegistryStatus(
                getPluginCount(),
                stateCounts.getOrDefault(PluginState.ENABLED, 0L).intValue(),
                stateCounts.getOrDefault(PluginState.DISABLED, 0L).intValue(),
                stateCounts.getOrDefault(PluginState.LOADED, 0L).intValue(),
                stateCounts.getOrDefault(PluginState.ERROR, 0L).intValue()
        );
    }

    /**
     * Validates that plugin can transition to enabled state
     *
     * @param name Name of the plugin
     * @throws PluginStateException if plugin cannot be enabled from current state
     */
    public void validateCanEnable(String name) {
        validatePluginName(name);
        PluginState currentState = getPluginState(name);
        if (!currentState.canEnable()) {
            throw new PluginStateException(name, currentState, PluginState.ENABLED);
        }
    }

    /**
     * Validates that plugin can transition to disabled state
     *
     * @param name Name of the plugin
     * @throws PluginStateException if plugin cannot be disabled from current state
     */
    public void validateCanDisable(String name) {
        validatePluginName(name);
        PluginState currentState = getPluginState(name);
        if (!currentState.canDisable()) {
            throw new PluginStateException(name, currentState, PluginState.DISABLED);
        }
    }

    public void clear() {
        plugins.clear();
        pluginStates.clear();
        log.debug("Registry cleared");
    }

    /**
     * Validates state transition according to rules
     *
     * @param fromState Current state
     * @param toState   Target state
     * @return true if transition is valid
     */
    private boolean isValidTransition(PluginState fromState, PluginState toState) {
        Set<PluginState> allowedTransitions = VALID_TRANSITIONS.get(fromState);
        return allowedTransitions != null && allowedTransitions.contains(toState);
    }

    /**
     * Validates plugin name
     *
     * @param name Plugin name to validate
     * @throws IllegalArgumentException if name is null or empty
     */
    private void validatePluginName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Plugin name cannot be null or empty");
        }
    }

    /**
     * Registry status data class
     */
    public static class RegistryStatus {
        private final int total;
        private final int enabled;
        private final int disabled;
        private final int loaded;
        private final int error;

        public RegistryStatus(int total, int enabled, int disabled, int loaded, int error) {
            this.total = total;
            this.enabled = enabled;
            this.disabled = disabled;
            this.loaded = loaded;
            this.error = error;
        }

        public int getTotal() {
            return total;
        }

        public int getEnabled() {
            return enabled;
        }

        public int getDisabled() {
            return disabled;
        }

        public int getLoaded() {
            return loaded;
        }

        public int getError() {
            return error;
        }

        @Override
        public String toString() {
            return String.format("RegistryStatus{total=%d, enabled=%d, disabled=%d, loaded=%d, error=%d}",
                    total, enabled, disabled, loaded, error);
        }
    }
}