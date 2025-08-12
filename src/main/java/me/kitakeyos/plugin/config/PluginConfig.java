package me.kitakeyos.plugin.config;

import java.util.List;
import java.util.Set;

public interface PluginConfig {
    String getString(String key);

    String getString(String key, String defaultValue);

    int getInt(String key);

    int getInt(String key, int defaultValue);

    long getLong(String key);

    long getLong(String key, long defaultValue);

    double getDouble(String key);

    double getDouble(String key, double defaultValue);

    boolean getBoolean(String key);

    boolean getBoolean(String key, boolean defaultValue);

    List<String> getStringList(String key);

    void set(String key, Object value);

    boolean contains(String key);

    Set<String> getKeys();

    void save();

    void reload();
}
