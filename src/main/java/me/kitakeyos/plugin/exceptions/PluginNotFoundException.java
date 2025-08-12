package me.kitakeyos.plugin.exceptions;

/**
 * Thrown when a plugin is not found in the registry
 */
public class PluginNotFoundException extends PluginException {
    public PluginNotFoundException(String pluginName) {
        super("Plugin not found", pluginName);
    }

    public PluginNotFoundException(String message, String pluginName) {
        super(message, pluginName);
    }
}
