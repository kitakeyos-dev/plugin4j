package me.kitakeyos.plugin.reload;

import lombok.Getter;

/**
 * Result of a hot reload operation
 * Contains success status, timing information, and error details
 */
@Getter
public class ReloadResult {
    private final boolean success;
    private final String pluginName;
    private final long durationMs;
    private final boolean statePreserved;
    private final String errorMessage;
    private final long timestamp;

    /**
     * Creates a reload result
     *
     * @param success        Whether the reload was successful
     * @param pluginName     Name of the plugin that was reloaded
     * @param durationMs     Duration of reload operation in milliseconds
     * @param statePreserved Whether plugin state was preserved
     * @param errorMessage   Error message if reload failed
     */
    private ReloadResult(boolean success, String pluginName, long durationMs,
                         boolean statePreserved, String errorMessage) {
        this.success = success;
        this.pluginName = pluginName;
        this.durationMs = durationMs;
        this.statePreserved = statePreserved;
        this.errorMessage = errorMessage;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Creates a successful reload result
     *
     * @param pluginName     Name of successfully reloaded plugin
     * @param durationMs     Duration of reload operation
     * @param statePreserved Whether state was preserved during reload
     * @return Success result
     */
    public static ReloadResult success(String pluginName, long durationMs, boolean statePreserved) {
        return new ReloadResult(true, pluginName, durationMs, statePreserved, null);
    }

    /**
     * Creates a failed reload result
     *
     * @param errorMessage Description of what went wrong
     * @return Failure result
     */
    public static ReloadResult failure(String errorMessage) {
        return new ReloadResult(false, null, 0, false, errorMessage);
    }
}