package me.kitakeyos.plugin.exceptions;

import lombok.Getter;

/**
 * Thrown when plugin operations fail (enable, disable, load, etc.)
 */
@Getter
public class PluginOperationException extends PluginException {
    private final PluginOperation operation;

    public PluginOperationException(PluginOperation operation, String pluginName, Throwable cause) {
        super(String.format("Failed to %s plugin", operation.toString().toLowerCase()), pluginName, cause);
        this.operation = operation;
    }

    public PluginOperationException(PluginOperation operation, String pluginName, String message) {
        super(String.format("Failed to %s plugin: %s", operation.toString().toLowerCase(), message), pluginName);
        this.operation = operation;
    }

    public enum PluginOperation {
        LOAD, ENABLE, DISABLE, RELOAD, UNLOAD
    }
}
