package me.kitakeyos.plugin.manager;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.kitakeyos.plugin.api.annotations.Extension;
import me.kitakeyos.plugin.api.annotations.ExtensionPoint;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages Extension Points and Extensions
 */
@Slf4j
public class ExtensionManager {

    // Map from Extension Point class -> List<Extension instances>
    private final Map<Class<?>, List<ExtensionWrapper>> extensions = new ConcurrentHashMap<>();

    // Map from plugin name -> List<Extension instances> (for cleanup when plugin unloads)
    private final Map<String, List<ExtensionWrapper>> pluginExtensions = new ConcurrentHashMap<>();

    /**
     * Register Extension Point
     */
    public void registerExtensionPoint(Class<?> extensionPointClass) {
        if (!extensionPointClass.isAnnotationPresent(ExtensionPoint.class)) {
            throw new IllegalArgumentException("Class must be annotated with @ExtensionPoint: " +
                    extensionPointClass.getName());
        }

        extensions.putIfAbsent(extensionPointClass, Collections.synchronizedList(new ArrayList<>()));

        ExtensionPoint annotation = extensionPointClass.getAnnotation(ExtensionPoint.class);
        log.info("Registered Extension Point: {} - {}",
                extensionPointClass.getSimpleName(), annotation.description());
    }

    /**
     * Register Extensions from plugin
     */
    public void registerExtensions(String pluginName, ClassLoader classLoader, List<Class<?>> extensionClasses) {
        List<ExtensionWrapper> pluginExtensionList = new ArrayList<>();

        for (Class<?> extensionClass : extensionClasses) {
            Extension annotation = extensionClass.getAnnotation(Extension.class);
            if (annotation == null || !annotation.enabled()) {
                continue;
            }

            // Find Extension Point interface
            Class<?> extensionPointClass = findExtensionPointInterface(extensionClass);
            if (extensionPointClass == null) {
                log.warn("No Extension Point interface found for: {}", extensionClass.getName());
                continue;
            }

            try {
                // Create extension instance
                Object extensionInstance = extensionClass.getDeclaredConstructor().newInstance();

                ExtensionWrapper wrapper = new ExtensionWrapper(
                        extensionInstance,
                        annotation,
                        extensionPointClass,
                        pluginName,
                        classLoader
                );

                // Register to Extension Point
                extensions.computeIfAbsent(extensionPointClass, k -> Collections.synchronizedList(new ArrayList<>()))
                        .add(wrapper);

                // Track by plugin
                pluginExtensionList.add(wrapper);

                log.info("Registered extension: {} for {} from plugin: {}",
                        extensionClass.getSimpleName(),
                        extensionPointClass.getSimpleName(),
                        pluginName);

            } catch (Exception e) {
                log.error("Failed to register extension: {} - {}", extensionClass.getName(), e.getMessage());
            }
        }

        // Save plugin extensions for cleanup later
        if (!pluginExtensionList.isEmpty()) {
            pluginExtensions.put(pluginName, pluginExtensionList);

            // Sort extensions by ordinal
            sortExtensions();
        }
    }

    /**
     * Find Extension Point interface
     */
    private Class<?> findExtensionPointInterface(Class<?> extensionClass) {
        // Check direct interfaces
        for (Class<?> iface : extensionClass.getInterfaces()) {
            if (iface.isAnnotationPresent(ExtensionPoint.class)) {
                return iface;
            }
        }

        // Check parent class interfaces
        Class<?> superClass = extensionClass.getSuperclass();
        while (superClass != null && superClass != Object.class) {
            for (Class<?> iface : superClass.getInterfaces()) {
                if (iface.isAnnotationPresent(ExtensionPoint.class)) {
                    return iface;
                }
            }
            superClass = superClass.getSuperclass();
        }

        return null;
    }

    /**
     * Sort extensions by ordinal
     */
    private void sortExtensions() {
        for (List<ExtensionWrapper> extensionList : extensions.values()) {
            synchronized (extensionList) {
                extensionList.sort(Comparator.comparingInt(w -> w.getAnnotation().ordinal()));
            }
        }
    }

    /**
     * Get all extensions for Extension Point
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getExtensions(Class<T> extensionPointClass) {
        List<ExtensionWrapper> wrappers = extensions.getOrDefault(extensionPointClass, new ArrayList<>());

        return wrappers.stream()
                .filter(w -> extensionPointClass.isInstance(w.getInstance()))
                .map(w -> (T) w.getInstance())
                .collect(Collectors.toList());
    }

    /**
     * Get first extension (highest priority)
     */
    public <T> Optional<T> getExtension(Class<T> extensionPointClass) {
        List<T> extensions = getExtensions(extensionPointClass);
        return extensions.isEmpty() ? Optional.empty() : Optional.of(extensions.get(0));
    }

    /**
     * Get extensions by plugin
     */
    public <T> List<T> getExtensionsByPlugin(Class<T> extensionPointClass, String pluginName) {
        return getExtensions(extensionPointClass).stream()
                .filter(ext -> {
                    ExtensionWrapper wrapper = findWrapper(ext);
                    return wrapper != null && pluginName.equals(wrapper.getPluginName());
                })
                .collect(Collectors.toList());
    }

    /**
     * Find wrapper for extension instance
     */
    private ExtensionWrapper findWrapper(Object instance) {
        for (List<ExtensionWrapper> wrapperList : extensions.values()) {
            for (ExtensionWrapper wrapper : wrapperList) {
                if (wrapper.getInstance() == instance) {
                    return wrapper;
                }
            }
        }
        return null;
    }

    /**
     * Unregister extensions of plugin
     */
    public void unregisterPluginExtensions(String pluginName) {
        List<ExtensionWrapper> pluginExtensionList = pluginExtensions.remove(pluginName);
        if (pluginExtensionList == null) {
            return;
        }

        for (ExtensionWrapper wrapper : pluginExtensionList) {
            List<ExtensionWrapper> extensionList = extensions.get(wrapper.getExtensionPointClass());
            if (extensionList != null) {
                extensionList.remove(wrapper);
            }
        }

        log.info("Unregistered {} extensions from plugin: {}", pluginExtensionList.size(), pluginName);
    }

    /**
     * Get information about Extension Points
     */
    public Map<String, ExtensionPointInfo> getExtensionPointsInfo() {
        Map<String, ExtensionPointInfo> info = new HashMap<>();

        for (Map.Entry<Class<?>, List<ExtensionWrapper>> entry : extensions.entrySet()) {
            Class<?> extensionPointClass = entry.getKey();
            List<ExtensionWrapper> wrappers = entry.getValue();

            ExtensionPoint annotation = extensionPointClass.getAnnotation(ExtensionPoint.class);

            List<ExtensionInfo> extensionInfos = wrappers.stream()
                    .map(wrapper -> new ExtensionInfo(
                            wrapper.getInstance().getClass().getSimpleName(),
                            wrapper.getPluginName(),
                            wrapper.getAnnotation().ordinal(),
                            wrapper.getAnnotation().description()
                    ))
                    .collect(Collectors.toList());

            info.put(extensionPointClass.getSimpleName(),
                    new ExtensionPointInfo(
                            extensionPointClass.getName(),
                            annotation.description(),
                            extensionInfos
                    ));
        }

        return info;
    }

    /**
     * Clear all extensions
     */
    public void clearAll() {
        extensions.clear();
        pluginExtensions.clear();
        log.info("Cleared all extensions");
    }

    /**
     * Wrapper class for Extension
     */
    @Getter
    public static class ExtensionWrapper {
        // Getters
        private final Object instance;
        private final Extension annotation;
        private final Class<?> extensionPointClass;
        private final String pluginName;
        private final ClassLoader classLoader;

        public ExtensionWrapper(Object instance, Extension annotation,
                                Class<?> extensionPointClass, String pluginName, ClassLoader classLoader) {
            this.instance = instance;
            this.annotation = annotation;
            this.extensionPointClass = extensionPointClass;
            this.pluginName = pluginName;
            this.classLoader = classLoader;
        }

    }

    /**
     * Info classes for API
     */
    public static final class ExtensionPointInfo {
        private final String className;
        private final String description;
        private final List<ExtensionInfo> extensions;

        /**
         *
         */
        public ExtensionPointInfo(
                String className,
                String description,
                List<ExtensionInfo> extensions
        ) {
            this.className = className;
            this.description = description;
            this.extensions = extensions;
        }

        public String className() {
            return className;
        }

        public String description() {
            return description;
        }

        public List<ExtensionInfo> extensions() {
            return extensions;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            ExtensionPointInfo that = (ExtensionPointInfo) obj;
            return Objects.equals(this.className, that.className) &&
                    Objects.equals(this.description, that.description) &&
                    Objects.equals(this.extensions, that.extensions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(className, description, extensions);
        }

        @Override
        public String toString() {
            return "ExtensionPointInfo[" +
                    "className=" + className + ", " +
                    "description=" + description + ", " +
                    "extensions=" + extensions + ']';
        }
    }

    public static final class ExtensionInfo {
        private final String className;
        private final String pluginName;
        private final int ordinal;
        private final String description;

        public ExtensionInfo(
                String className,
                String pluginName,
                int ordinal,
                String description
        ) {
            this.className = className;
            this.pluginName = pluginName;
            this.ordinal = ordinal;
            this.description = description;
        }

        public String className() {
            return className;
        }

        public String pluginName() {
            return pluginName;
        }

        public int ordinal() {
            return ordinal;
        }

        public String description() {
            return description;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            ExtensionInfo that = (ExtensionInfo) obj;
            return Objects.equals(this.className, that.className) &&
                    Objects.equals(this.pluginName, that.pluginName) &&
                    this.ordinal == that.ordinal &&
                    Objects.equals(this.description, that.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(className, pluginName, ordinal, description);
        }

        @Override
        public String toString() {
            return "ExtensionInfo[" +
                    "className=" + className + ", " +
                    "pluginName=" + pluginName + ", " +
                    "ordinal=" + ordinal + ", " +
                    "description=" + description + ']';
        }
    }
}