package me.kitakeyos.plugin.exceptions;

import lombok.Getter;

/**
 * Thrown when plugin loading fails
 */
@Getter
public class PluginLoadException extends PluginException {
    private final String jarFile;

    public PluginLoadException(String jarFile, String message, Throwable cause) {
        super(String.format("Failed to load plugin from '%s': %s", jarFile, message), cause);
        this.jarFile = jarFile;
    }

    public PluginLoadException(String jarFile, Throwable cause) {
        super(String.format("Failed to load plugin from '%s'", jarFile), cause);
        this.jarFile = jarFile;
    }

}
