package me.kitakeyos.plugin.updater;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class UpdateResult {
    private final List<String> updatedPlugins;
    private final List<String> failedUpdates;
    private final long timestamp;

    public UpdateResult(List<String> updatedPlugins, List<String> failedUpdates) {
        this.updatedPlugins = Collections.unmodifiableList(updatedPlugins);
        this.failedUpdates = Collections.unmodifiableList(failedUpdates);
        this.timestamp = System.currentTimeMillis();
    }

    public boolean hasUpdates() {
        return !updatedPlugins.isEmpty();
    }

    public boolean hasFailures() {
        return !failedUpdates.isEmpty();
    }

    public boolean isSuccessful() {
        return hasUpdates() && !hasFailures();
    }

    public static UpdateResult noUpdates() {
        return new UpdateResult(Collections.emptyList(), Collections.emptyList());
    }

    @Override
    public String toString() {
        return String.format("UpdateResult{updated=%d, failed=%d, timestamp=%d}",
                updatedPlugins.size(), failedUpdates.size(), timestamp);
    }
}
