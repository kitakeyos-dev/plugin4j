package me.kitakeyos.plugin.api;

public interface HotReloadAware {
    /**
     * Check if the plugin can be safely hot reloaded
     */
    boolean canHotReload();

    /**
     * Prepare for hot reload (cleanup, save state, etc.)
     */
    void prepareForReload();

    /**
     * Called after successful hot reload
     */
    default void onHotReloadComplete() {
        // Default empty implementation
    }
}