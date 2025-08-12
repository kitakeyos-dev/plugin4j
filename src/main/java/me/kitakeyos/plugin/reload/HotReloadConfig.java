package me.kitakeyos.plugin.reload;

import lombok.Builder;
import lombok.Getter;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration class for hot reload functionality
 * Defines behavior, limits, and options for plugin hot reloading
 */
@Getter
@Builder
public class HotReloadConfig {
    /**
     * Whether automatic reloading is enabled when files change
     */
    @Builder.Default
    private final boolean autoReloadEnabled = true;

    /**
     * Maximum number of concurrent reload operations
     */
    @Builder.Default
    private final int maxConcurrentReloads = 3;

    /**
     * Debounce time for file changes to avoid rapid successive reloads
     */
    @Builder.Default
    private final long fileChangeDebounceMs = 1000;

    /**
     * Directory for storing plugin state snapshots
     */
    @Builder.Default
    private final Path stateDirectory = Paths.get("plugin-states");

    /**
     * Whether to collect reload metrics
     */
    @Builder.Default
    private final boolean enableMetrics = true;

    /**
     * Whether rollback functionality is enabled
     */
    @Builder.Default
    private final boolean enableRollback = true;

    /**
     * Maximum time to retain state snapshots
     */
    @Builder.Default
    private final long maxStateRetentionMs = 24 * 60 * 60 * 1000; // 24 hours

    /**
     * Maximum number of state backups to keep per plugin
     */
    @Builder.Default
    private final int maxStateBackups = 5;

    /**
     * Whether to compress state snapshots
     */
    @Builder.Default
    private final boolean compressStates = true;

    /**
     * Default timeout for graceful plugin shutdown
     */
    @Builder.Default
    private final long defaultShutdownTimeoutMs = 10000;

    /**
     * Whether to allow concurrent reloads of different plugins
     */
    @Builder.Default
    private final boolean allowConcurrentPluginReloads = false;

    /**
     * File extensions to monitor for changes
     */
    @Builder.Default
    private final String[] watchedFileExtensions = {".jar"};

    /**
     * Whether to validate file checksums for change detection
     */
    @Builder.Default
    private final boolean validateChecksums = true;

    /**
     * Creates a default configuration suitable for most environments
     *
     * @return Default hot reload configuration
     */
    public static HotReloadConfig createDefault() {
        return HotReloadConfig.builder().build();
    }

    /**
     * Creates a development-friendly configuration with faster reloads
     *
     * @return Development-optimized configuration
     */
    public static HotReloadConfig createDevelopment() {
        return HotReloadConfig.builder()
                .autoReloadEnabled(true)
                .fileChangeDebounceMs(500)
                .maxConcurrentReloads(5)
                .enableMetrics(true)
                .validateChecksums(false) // Skip for faster reloads
                .build();
    }

    /**
     * Creates a production-safe configuration with conservative settings
     *
     * @return Production-optimized configuration
     */
    public static HotReloadConfig createProduction() {
        return HotReloadConfig.builder()
                .autoReloadEnabled(false) // Manual reloads only
                .maxConcurrentReloads(1)
                .fileChangeDebounceMs(2000)
                .enableRollback(true)
                .validateChecksums(true)
                .maxStateRetentionMs(7 * 24 * 60 * 60 * 1000) // 7 days
                .build();
    }
}