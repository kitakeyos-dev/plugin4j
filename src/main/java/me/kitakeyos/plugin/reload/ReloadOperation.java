package me.kitakeyos.plugin.reload;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks the progress and state of a hot reload operation
 * Provides detailed logging and phase tracking for debugging
 */
@Getter
public class ReloadOperation {
    private final String pluginName;
    private final HotReloadOptions options;
    private final long startTime;
    private final String operationId;

    @Setter
    private PluginStateSnapshot stateSnapshot;

    private final AtomicReference<ReloadPhase> currentPhase = new AtomicReference<>(ReloadPhase.INITIALIZING);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicLong phaseStartTime = new AtomicLong();
    private final StringBuilder log = new StringBuilder();

    /**
     * Creates a new reload operation
     *
     * @param pluginName Name of plugin being reloaded
     * @param options    Options controlling reload behavior
     */
    public ReloadOperation(String pluginName, HotReloadOptions options) {
        this.pluginName = pluginName;
        this.options = options;
        this.startTime = System.currentTimeMillis();
        this.operationId = generateOperationId();
        this.phaseStartTime.set(startTime);
    }

    /**
     * Transitions to a new reload phase and logs timing
     *
     * @param phase New phase to transition to
     */
    public void setPhase(ReloadPhase phase) {
        ReloadPhase oldPhase = currentPhase.getAndSet(phase);
        long now = System.currentTimeMillis();
        long phaseDuration = now - phaseStartTime.getAndSet(now);

        log.append(String.format("[%s] %s -> %s (took %dms)\n",
                operationId, oldPhase, phase, phaseDuration));
    }

    /**
     * Adds a log entry for the current phase
     *
     * @param message Log message to add
     */
    public void addLog(String message) {
        log.append(String.format("[%s] %s: %s\n",
                operationId, currentPhase.get(), message));
    }

    /**
     * Cancels the reload operation
     */
    public void cancel() {
        cancelled.set(true);
        addLog("Operation cancelled");
    }

    /**
     * Checks if operation has been cancelled
     *
     * @return true if operation is cancelled
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * Gets total duration of operation so far
     *
     * @return Duration in milliseconds
     */
    public long getDuration() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Gets complete operation log
     *
     * @return Formatted log string
     */
    public String getLog() {
        return log.toString();
    }

    /**
     * Generates unique operation ID for tracking
     *
     * @return Unique operation identifier
     */
    private String generateOperationId() {
        return pluginName + "-" + System.currentTimeMillis() + "-" +
                Integer.toHexString(System.identityHashCode(this));
    }

    /**
     * Enumeration of reload operation phases
     */
    public enum ReloadPhase {
        /**
         * Initial setup and validation
         */
        INITIALIZING,
        /**
         * Pre-reload validation checks
         */
        VALIDATING,
        /**
         * Capturing current plugin state
         */
        CAPTURING_STATE,
        /**
         * Graceful shutdown of plugin
         */
        GRACEFUL_SHUTDOWN,
        /**
         * Disabling and unloading plugin
         */
        DISABLING,
        /**
         * Loading new plugin version
         */
        LOADING_NEW_VERSION,
        /**
         * Restoring captured state
         */
        RESTORING_STATE,
        /**
         * Enabling new plugin version
         */
        ENABLING,
        /**
         * Operation completed successfully
         */
        COMPLETED,
        /**
         * Rolling back due to failure
         */
        ROLLING_BACK,
        /**
         * Operation failed
         */
        FAILED
    }
}