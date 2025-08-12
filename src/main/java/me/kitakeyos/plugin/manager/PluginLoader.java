package me.kitakeyos.plugin.manager;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.kitakeyos.plugin.api.BasePlugin;
import me.kitakeyos.plugin.api.PluginMetadata;
import me.kitakeyos.plugin.api.annotations.Extension;
import me.kitakeyos.plugin.api.annotations.ExtensionPoint;
import me.kitakeyos.plugin.api.annotations.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Enhanced PluginLoader with Extension Point scanning capability
 * Handles loading plugins from JAR files and manages temporary files
 * Now includes automatic Extension Point and Extension discovery
 */
@Slf4j
public class PluginLoader {

    // Track temp files and class loaders for cleanup
    private final Map<String, TempPluginData> tempPluginData = new ConcurrentHashMap<>();
    private final Path tempDirectory;

    /**
     * -- SETTER --
     * Set extension manager (called by PluginManager)
     */
    // Extension manager for handling extension points
    @Setter
    private ExtensionManager extensionManager;

    /**
     * Initializes plugin loader with temporary directory and shutdown hook
     * Creates temp directory for plugin JAR files and registers cleanup on shutdown
     */
    public PluginLoader() {
        this.tempDirectory = createTempDirectory();
        log.info("Plugin temp directory: {}", tempDirectory);
    }

    /**
     * Creates temporary directory for plugin files
     *
     * @return Path to the created temporary directory
     * @throws RuntimeException if directory creation fails
     */
    private Path createTempDirectory() {
        try {
            Path tempDir = Files.createTempDirectory("plugins-");
            // Mark for deletion on exit as backup cleanup
            tempDir.toFile().deleteOnExit();
            return tempDir;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp directory", e);
        }
    }

    /**
     * Loads plugin metadata from JAR file
     * First tries to read from plugin.yml, then falls back to @Plugin annotation
     *
     * @param jarFile The JAR file to load metadata from
     * @return PluginMetadata extracted from the JAR file
     * @throws Exception if metadata cannot be loaded or is invalid
     */
    public PluginMetadata loadMetadata(File jarFile) throws Exception {
        try (JarFile jar = new JarFile(jarFile)) {
            // First try to read plugin.ini
            JarEntry pluginIni = jar.getJarEntry("plugin.ini");
            if (pluginIni != null) {
                return loadMetadataFromFile(jar, pluginIni, jarFile);
            }

            // Fallback: scan for @Plugin annotation
            return loadMetadataFromAnnotation(jarFile);
        }
    }

    /**
     * Loads plugin instance with proper temporary file management
     * Creates temporary copy of JAR file and custom class loader
     * Now includes Extension Point and Extension scanning
     *
     * @param jarFile  The original JAR file
     * @param metadata Plugin metadata
     * @return BasePlugin instance loaded from the JAR
     * @throws Exception if plugin loading fails
     */
    public BasePlugin loadPlugin(File jarFile, PluginMetadata metadata) throws Exception {
        String pluginName = metadata.getName();

        // Cleanup old temp data if exists
        cleanupPlugin(pluginName);

        try {
            // Create temp file with unique name
            Path tempJarFile = createTempJarFile(jarFile, pluginName);

            // Create custom class loader
            URLClassLoader classLoader = new PluginClassLoader(
                    new URL[]{tempJarFile.toUri().toURL()},
                    this.getClass().getClassLoader(),
                    pluginName
            );

            // Load main class
            Class<?> mainClass = classLoader.loadClass(metadata.getMainClass());

            if (!BasePlugin.class.isAssignableFrom(mainClass)) {
                throw new IllegalArgumentException("Main class must extend BasePlugin: " + metadata.getMainClass());
            }

            // Create plugin instance
            BasePlugin plugin = (BasePlugin) mainClass.getDeclaredConstructor().newInstance();
            plugin.setMetadata(metadata);

            // Store temp data for cleanup
            TempPluginData tempData = new TempPluginData(tempJarFile, classLoader, System.currentTimeMillis());
            tempPluginData.put(pluginName, tempData);

            // Scan and register Extension Points and Extensions
            if (extensionManager != null) {
                scanAndRegisterExtensions(jarFile, pluginName, classLoader);
            }

            log.info("Successfully loaded plugin: {} from temp file: {}", pluginName, tempJarFile);

            return plugin;

        } catch (Exception e) {
            // Cleanup on error
            cleanupPlugin(pluginName);
            throw e;
        }
    }

    /**
     * Scan JAR file to find Extension Points and Extensions
     * Automatically registers found Extension Points and Extensions
     *
     * @param jarFile     The JAR file to scan
     * @param pluginName  Name of the plugin
     * @param classLoader Class loader for the plugin
     */
    private void scanAndRegisterExtensions(File jarFile, String pluginName, ClassLoader classLoader) {
        try (JarFile jar = new JarFile(jarFile)) {
            List<Class<?>> extensionClasses = new ArrayList<>();

            // Scan all .class files
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    String className = entry.getName()
                            .replace('/', '.')
                            .replace(".class", "");

                    try {
                        Class<?> clazz = classLoader.loadClass(className);

                        // Register Extension Point
                        if (clazz.isInterface() && clazz.isAnnotationPresent(ExtensionPoint.class)) {
                            extensionManager.registerExtensionPoint(clazz);
                        }

                        // Collect Extension classes
                        if (clazz.isAnnotationPresent(Extension.class)) {
                            extensionClasses.add(clazz);
                        }

                    } catch (ClassNotFoundException | NoClassDefFoundError e) {
                        // Skip classes that can't be loaded
                        log.debug("Skipping class that couldn't be loaded: {}", className);
                    }
                }
            }

            // Register all Extensions
            if (!extensionClasses.isEmpty()) {
                extensionManager.registerExtensions(pluginName, classLoader, extensionClasses);
            }

        } catch (Exception e) {
            log.error("Failed to scan extensions for plugin: {} - {}", pluginName, e.getMessage());
        }
    }

    /**
     * Creates temporary JAR file with unique name to avoid file locking issues
     *
     * @param originalJar The original JAR file to copy
     * @param pluginName  Name of the plugin (used in temp file name)
     * @return Path to the created temporary JAR file
     * @throws IOException if file copying fails
     */
    private Path createTempJarFile(File originalJar, String pluginName) throws IOException {
        // Create unique temp file name with timestamp
        String timestamp = String.valueOf(System.currentTimeMillis());
        String tempFileName = pluginName + "_" + timestamp + ".jar";
        Path tempJarPath = tempDirectory.resolve(tempFileName);

        // Copy JAR to temp location
        Files.copy(originalJar.toPath(), tempJarPath, StandardCopyOption.REPLACE_EXISTING);

        // Mark for deletion on exit (backup cleanup)
        tempJarPath.toFile().deleteOnExit();

        log.debug("Created temp JAR: {}", tempJarPath);
        return tempJarPath;
    }

    /**
     * Cleans up temporary data for a specific plugin
     * Closes class loader and deletes temporary files
     *
     * @param pluginName Name of the plugin to cleanup
     */
    public void cleanupPlugin(String pluginName) {
        TempPluginData tempData = tempPluginData.remove(pluginName);
        if (tempData != null) {
            cleanupTempData(tempData, pluginName);
        }
    }

    /**
     * Internal method to cleanup temporary data structure
     *
     * @param tempData   The temporary data to cleanup
     * @param pluginName Name of the plugin (for logging)
     */
    private void cleanupTempData(TempPluginData tempData, String pluginName) {
        // Close class loader first to release file handles
        try {
            tempData.classLoader().close();
            log.debug("Closed class loader for plugin: {}", pluginName);
        } catch (IOException e) {
            log.warn("Failed to close class loader for plugin {}: {}", pluginName, e.getMessage());
        }

        // Delete temp JAR file
        try {
            Files.deleteIfExists(tempData.tempJarPath());
            log.debug("Deleted temp JAR for plugin: {} - {}", pluginName, tempData.tempJarPath());
        } catch (IOException e) {
            log.warn("Failed to delete temp JAR for plugin {}: {}", pluginName, e.getMessage());
        }
    }

    /**
     * Cleans up all temporary files and directories
     * Called during shutdown to ensure proper cleanup
     */
    public void cleanupAllTempFiles() {
        log.info("Cleaning up all plugin temp files...");

        // Cleanup all tracked temp data
        for (Map.Entry<String, TempPluginData> entry : tempPluginData.entrySet()) {
            cleanupTempData(entry.getValue(), entry.getKey());
        }
        tempPluginData.clear();

        // Cleanup temp directory and all contents
        try {
            if (Files.exists(tempDirectory)) {
                Files.walk(tempDirectory)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                log.info("Cleaned up temp directory: {}", tempDirectory);
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup temp directory: {}", e.getMessage());
        }
    }

    /**
     * Gets statistics about current temporary files
     *
     * @return TempFileStats containing file count, total size, and directory path
     */
    public TempFileStats getTempFileStats() {
        long totalSize = 0;
        int fileCount = tempPluginData.size();

        // Calculate total size of temp files
        for (TempPluginData data : tempPluginData.values()) {
            try {
                if (Files.exists(data.tempJarPath())) {
                    totalSize += Files.size(data.tempJarPath());
                }
            } catch (IOException e) {
                // Ignore and continue
            }
        }

        return new TempFileStats(fileCount, totalSize, tempDirectory.toString());
    }

    /**
     * Periodic cleanup of old temporary files
     * Should be called periodically to clean up stale temp files
     *
     * @param maxAgeMs Maximum age in milliseconds before cleanup
     */
    public void cleanupOldTempFiles(long maxAgeMs) {
        long currentTime = System.currentTimeMillis();

        // Find plugins with old temp files
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, TempPluginData> entry : tempPluginData.entrySet()) {
            TempPluginData data = entry.getValue();
            if (currentTime - data.creationTime() > maxAgeMs) {
                toRemove.add(entry.getKey());
            }
        }

        // Cleanup old temp data
        for (String pluginName : toRemove) {
            log.info("Cleaning up old temp data for plugin: {}", pluginName);
            cleanupPlugin(pluginName);
        }
    }

    /**
     * Loads plugin metadata from plugin.yml file
     *
     * @param jar     The JAR file container
     * @param entry   The plugin.ini JAR entry
     * @param jarFile The original JAR file
     * @return PluginMetadata parsed from plugin.ini
     * @throws Exception if parsing fails or required fields are missing
     */
    private PluginMetadata loadMetadataFromFile(JarFile jar, JarEntry entry, File jarFile) throws Exception {
        try (InputStream is = jar.getInputStream(entry)) {
            Properties props = new Properties();
            props.load(is);

            // Extract required fields
            String name = props.getProperty("name");
            String version = props.getProperty("version");
            String description = props.getProperty("description", "");
            String author = props.getProperty("author", "");
            String mainClass = props.getProperty("main");

            // Validate required fields
            if (name == null || version == null || mainClass == null) {
                throw new IllegalArgumentException(entry.getName() + " must contain name, version, and main properties");
            }

            // Parse dependencies
            List<String> dependencies = new ArrayList<>();
            String depsStr = props.getProperty("dependencies");
            if (depsStr != null && !depsStr.trim().isEmpty()) {
                dependencies.addAll(Arrays.asList(depsStr.split(",")));
                dependencies.replaceAll(String::trim);
            }
            return new PluginMetadata(name, version, description, author,
                    mainClass, dependencies, jarFile);
        }
    }

    /**
     * Loads plugin metadata from @Plugin annotation by scanning classes
     *
     * @param jarFile The JAR file to scan
     * @return PluginMetadata extracted from @Plugin annotation
     * @throws Exception if no valid plugin annotation is found
     */
    private PluginMetadata loadMetadataFromAnnotation(File jarFile) throws Exception {
        try (JarFile jar = new JarFile(jarFile)) {
            try (URLClassLoader tempLoader = new URLClassLoader(
                    new URL[]{jarFile.toURI().toURL()},
                    this.getClass().getClassLoader()
            )) {

                // Scan all .class files in JAR
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".class")) {
                        String className = entry.getName()
                                .replace('/', '.')
                                .replace(".class", "");

                        try {
                            Class<?> clazz = tempLoader.loadClass(className);

                            // Check for @Plugin annotation and BasePlugin inheritance
                            if (clazz.isAnnotationPresent(Plugin.class) && BasePlugin.class.isAssignableFrom(clazz)) {
                                Plugin annotation = clazz.getAnnotation(Plugin.class);

                                return new PluginMetadata(
                                        annotation.name(),
                                        annotation.version(),
                                        annotation.description(),
                                        annotation.author(),
                                        className,
                                        Arrays.asList(annotation.dependencies()),
                                        jarFile
                                );
                            }
                        } catch (ClassNotFoundException | NoClassDefFoundError e) {
                            // Skip classes that can't be loaded
                        }
                    }
                }
            }
        }

        throw new IllegalArgumentException("No valid plugin found in JAR file");
    }

    /**
     * Custom class loader with better tracking for plugin isolation
     */
    @Getter
    private static class PluginClassLoader extends URLClassLoader {
        /**
         * -- GETTER --
         *  Gets the name of the plugin this class loader belongs to
         *
         */
        private final String pluginName;

        /**
         * Creates a new plugin class loader
         *
         * @param urls       URLs to load classes from
         * @param parent     Parent class loader
         * @param pluginName Name of the plugin (for tracking)
         */
        public PluginClassLoader(URL[] urls, ClassLoader parent, String pluginName) {
            super(urls, parent);
            this.pluginName = pluginName;
        }

        /**
         * Closes the class loader and logs the action
         */
        @Override
        public void close() throws IOException {
            log.debug("Closing class loader for plugin: {}", pluginName);
            super.close();
        }

    }

    /**
     * Container for temporary plugin data
     */
    private static final class TempPluginData {
        private final Path tempJarPath;
        private final URLClassLoader classLoader;
        private final long creationTime;

        /**
         * @param tempJarPath  Path to temporary JAR file
         * @param classLoader  Class loader for the plugin
         * @param creationTime Timestamp when temp data was created
         */
        private TempPluginData(Path tempJarPath, URLClassLoader classLoader, long creationTime) {
            this.tempJarPath = tempJarPath;
            this.classLoader = classLoader;
            this.creationTime = creationTime;
        }

        public Path tempJarPath() {
            return tempJarPath;
        }

        public URLClassLoader classLoader() {
            return classLoader;
        }

        public long creationTime() {
            return creationTime;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            TempPluginData that = (TempPluginData) obj;
            return Objects.equals(this.tempJarPath, that.tempJarPath) &&
                    Objects.equals(this.classLoader, that.classLoader) &&
                    this.creationTime == that.creationTime;
        }

        @Override
        public int hashCode() {
            return Objects.hash(tempJarPath, classLoader, creationTime);
        }

        @Override
        public String toString() {
            return "TempPluginData[" +
                    "tempJarPath=" + tempJarPath + ", " +
                    "classLoader=" + classLoader + ", " +
                    "creationTime=" + creationTime + ']';
        }

    }

    /**
     * Statistics about temporary files
     */
    public static final class TempFileStats {
        private final int fileCount;
        private final long totalSize;
        private final String tempDirectory;

        /**
         * @param fileCount     Number of temporary files
         * @param totalSize     Total size of temporary files in bytes
         * @param tempDirectory Path to temporary directory
         */
        public TempFileStats(int fileCount, long totalSize, String tempDirectory) {
            this.fileCount = fileCount;
            this.totalSize = totalSize;
            this.tempDirectory = tempDirectory;
        }

        @Override
        public String toString() {
            return String.format("TempFileStats{files=%d, size=%d bytes (%.2f MB), dir='%s'}",
                    fileCount, totalSize, totalSize / 1024.0 / 1024.0, tempDirectory);
        }

        public int fileCount() {
            return fileCount;
        }

        public long totalSize() {
            return totalSize;
        }

        public String tempDirectory() {
            return tempDirectory;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            TempFileStats that = (TempFileStats) obj;
            return this.fileCount == that.fileCount &&
                    this.totalSize == that.totalSize &&
                    Objects.equals(this.tempDirectory, that.tempDirectory);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fileCount, totalSize, tempDirectory);
        }

    }
}