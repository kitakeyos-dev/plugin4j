package me.kitakeyos.plugin.exceptions;

import lombok.Getter;

/**
 * Base exception for all plugin-related errors
 */
@Getter
public class PluginException extends RuntimeException {
    private final String pluginName;

    public PluginException(String message) {
        this(message, null, null);
    }

    public PluginException(String message, String pluginName) {
        this(message, pluginName, null);
    }

    public PluginException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public PluginException(String message, String pluginName, Throwable cause) {
        super(message, cause);
        this.pluginName = pluginName;
    }

    @Override
    public String getMessage() {
        String baseMessage = super.getMessage();
        if (pluginName != null) {
            return String.format("[Plugin: %s] %s", pluginName, baseMessage);
        }
        return baseMessage;
    }
}