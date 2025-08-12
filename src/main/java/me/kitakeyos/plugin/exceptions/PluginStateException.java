package me.kitakeyos.plugin.exceptions;

import lombok.Getter;
import me.kitakeyos.plugin.manager.PluginState;

/**
 * Thrown when plugin state transitions are invalid
 */
@Getter
public class PluginStateException extends PluginException {
    private final PluginState fromState;
    private final PluginState toState;

    public PluginStateException(String pluginName, PluginState fromState, PluginState toState) {
        super(String.format("Invalid state transition: %s -> %s", fromState, toState), pluginName);
        this.fromState = fromState;
        this.toState = toState;
    }

}
