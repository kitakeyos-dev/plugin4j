package me.kitakeyos.plugin.config;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigManager {
    private final File dataDirectory;
    private final Map<String, PluginConfig> configs = new ConcurrentHashMap<>();
    private final PropertiesConfigLoader propertiesLoader = new PropertiesConfigLoader();

    public ConfigManager(File dataDirectory) {
        this.dataDirectory = dataDirectory;
        if (!dataDirectory.exists()) {
            dataDirectory.mkdirs();
        }
    }

    public PluginConfig getConfig(String pluginName) {
        return configs.computeIfAbsent(pluginName, name -> {
            File pluginDir = new File(dataDirectory, name);
            if (!pluginDir.exists()) {
                pluginDir.mkdirs();
            }

            // Changed from config.yml to config.properties
            File configFile = new File(pluginDir, "config.properties");
            return propertiesLoader.loadConfig(configFile);
        });
    }

    public void saveAllConfigs() {
        configs.values().forEach(PluginConfig::save);
    }

    public void reloadConfig(String pluginName) {
        PluginConfig config = configs.get(pluginName);
        if (config != null) {
            config.reload();
        }
    }

    public void removeConfig(String pluginName) {
        PluginConfig config = configs.remove(pluginName);
        if (config != null) {
            // Optional: save before removing
            config.save();
        }
    }

    public boolean hasConfig(String pluginName) {
        return configs.containsKey(pluginName);
    }

    public void shutdown() {
        saveAllConfigs();
        configs.clear();
    }
}