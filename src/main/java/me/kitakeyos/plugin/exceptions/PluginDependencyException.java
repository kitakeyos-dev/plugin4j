package me.kitakeyos.plugin.exceptions;

import lombok.Getter;

/**
 * Thrown when plugin dependencies cannot be resolved
 */
@Getter
public class PluginDependencyException extends PluginException {
    private final String dependencyName;

    public PluginDependencyException(String pluginName, String dependencyName, String message) {
        super(String.format("Dependency issue with '%s': %s", dependencyName, message), pluginName);
        this.dependencyName = dependencyName;
    }

}
