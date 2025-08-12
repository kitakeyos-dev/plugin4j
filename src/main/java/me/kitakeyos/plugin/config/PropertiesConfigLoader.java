package me.kitakeyos.plugin.config;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class PropertiesConfigLoader {

    public PluginConfig loadConfig(File configFile) {
        return new PropertiesPluginConfig(configFile);
    }

    private static class PropertiesPluginConfig implements PluginConfig {
        private final File configFile;
        private final Properties properties = new Properties();
        private final Map<String, Object> cache = new ConcurrentHashMap<>();

        public PropertiesPluginConfig(File configFile) {
            this.configFile = configFile;
            reload();
        }

        @Override
        public String getString(String key) {
            return getString(key, null);
        }

        @Override
        public String getString(String key, String defaultValue) {
            return properties.getProperty(key, defaultValue);
        }

        @Override
        public int getInt(String key) {
            return getInt(key, 0);
        }

        @Override
        public int getInt(String key, int defaultValue) {
            String value = properties.getProperty(key);
            if (value != null) {
                try {
                    return Integer.parseInt(value.trim());
                } catch (NumberFormatException e) {
                    log.warn("Invalid integer value for key '{}': {}", key, value);
                }
            }
            return defaultValue;
        }

        @Override
        public long getLong(String key) {
            return getLong(key, 0L);
        }

        @Override
        public long getLong(String key, long defaultValue) {
            String value = properties.getProperty(key);
            if (value != null) {
                try {
                    return Long.parseLong(value.trim());
                } catch (NumberFormatException e) {
                    log.warn("Invalid long value for key '{}': {}", key, value);
                }
            }
            return defaultValue;
        }

        @Override
        public double getDouble(String key) {
            return getDouble(key, 0.0);
        }

        @Override
        public double getDouble(String key, double defaultValue) {
            String value = properties.getProperty(key);
            if (value != null) {
                try {
                    return Double.parseDouble(value.trim());
                } catch (NumberFormatException e) {
                    log.warn("Invalid double value for key '{}': {}", key, value);
                }
            }
            return defaultValue;
        }

        @Override
        public boolean getBoolean(String key) {
            return getBoolean(key, false);
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            String value = properties.getProperty(key);
            if (value != null) {
                String trimmed = value.trim().toLowerCase();
                return "true".equals(trimmed) || "yes".equals(trimmed) || "1".equals(trimmed) || "on".equals(trimmed);
            }
            return defaultValue;
        }

        @Override
        public List<String> getStringList(String key) {
            String value = properties.getProperty(key);
            if (value != null && !value.trim().isEmpty()) {
                // Support comma-separated values
                String[] parts = value.split(",");
                List<String> result = new ArrayList<>();
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        result.add(trimmed);
                    }
                }
                return result;
            }
            return new ArrayList<>();
        }

        @Override
        public void set(String key, Object value) {
            if (key == null || key.trim().isEmpty()) {
                throw new IllegalArgumentException("Key cannot be null or empty");
            }

            if (value == null) {
                properties.remove(key);
                cache.remove(key);
            } else if (value instanceof List<?>) {
                List<?> list = (List<?>) value;
                // Convert list to comma-separated string
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(list.get(i).toString());
                }
                properties.setProperty(key, sb.toString());
                cache.put(key, value);
            } else {
                properties.setProperty(key, value.toString());
                cache.put(key, value);
            }
        }

        @Override
        public boolean contains(String key) {
            return properties.containsKey(key);
        }

        @Override
        public Set<String> getKeys() {
            return properties.stringPropertyNames();
        }

        @Override
        public void save() {
            try {
                // Ensure parent directory exists
                File parentDir = configFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }

                try (FileOutputStream fos = new FileOutputStream(configFile);
                     OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {

                    properties.store(osw, "Plugin Configuration - Generated on " + new Date());
                    log.info("Config saved to: {}", configFile.getAbsolutePath());
                }
            } catch (IOException e) {
                log.error("Failed to save config to {}: {}", configFile.getAbsolutePath(), e.getMessage());
            }
        }

        @Override
        public void reload() {
            properties.clear();
            cache.clear();

            if (configFile.exists()) {
                try (FileInputStream fis = new FileInputStream(configFile);
                     InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8)) {

                    properties.load(isr);
                    log.info("Config loaded from: {}", configFile.getAbsolutePath());

                } catch (IOException e) {
                    log.error("Failed to load config from {}: {}", configFile.getAbsolutePath(), e.getMessage());
                }
            } else {
                // Create default config
                setDefaults();
                save();
                log.info("Created default config at: {}", configFile.getAbsolutePath());
            }
        }

        private void setDefaults() {
            // Default configuration
            set("plugin.enabled", true);
            set("plugin.debug", false);
        }
    }
}