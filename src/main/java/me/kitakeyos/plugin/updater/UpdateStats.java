package me.kitakeyos.plugin.updater;

import lombok.Getter;

@Getter
public class UpdateStats {
    private final int availableUpdates;
    private final int backupCount;
    private final long updateDirectorySize;
    private final long backupDirectorySize;

    public UpdateStats(int availableUpdates, int backupCount,
                       long updateDirectorySize, long backupDirectorySize) {
        this.availableUpdates = availableUpdates;
        this.backupCount = backupCount;
        this.updateDirectorySize = updateDirectorySize;
        this.backupDirectorySize = backupDirectorySize;
    }

    @Override
    public String toString() {
        return String.format("UpdateStats{updates=%d, backups=%d, updateSize=%d bytes, backupSize=%d bytes}",
                availableUpdates, backupCount, updateDirectorySize, backupDirectorySize);
    }
}
