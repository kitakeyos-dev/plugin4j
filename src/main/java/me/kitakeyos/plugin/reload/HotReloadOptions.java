package me.kitakeyos.plugin.reload;

import lombok.Builder;
import lombok.Getter;

/**
 * Options for individual hot reload operations
 * Controls behavior of specific reload attempts
 */
@Getter
@Builder
public class HotReloadOptions {
    /**
     * Whether to preserve plugin state during reload
     */
    private final boolean preserveState;
    /**
     * Whether to force reload even if validation fails
     */
    private final boolean forceReload;
    /**
     * Whether to clear class loader cache
     */
    private final boolean clearClassCache;
    /**
     * Timeout for graceful plugin shutdown
     */
    private final long shutdownTimeoutMs;

    /**
     * Creates default reload options with safe settings
     *
     * @return Default hot reload options
     */
    public static HotReloadOptions defaultOptions() {
        return HotReloadOptions.builder()
                .preserveState(true)
                .forceReload(false)
                .clearClassCache(true)
                .shutdownTimeoutMs(10000) // 10 seconds
                .build();
    }

    /**
     * Creates options optimized for automatic reloads
     * Uses shorter timeouts for faster automatic reloading
     *
     * @return Auto-reload optimized options
     */
    public static HotReloadOptions autoReloadOptions() {
        return HotReloadOptions.builder()
                .preserveState(true)
                .forceReload(false)
                .clearClassCache(true)
                .shutdownTimeoutMs(5000) // 5 seconds for auto-reload
                .build();
    }
}