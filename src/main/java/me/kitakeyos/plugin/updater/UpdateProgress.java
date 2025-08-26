package me.kitakeyos.plugin.updater;

import lombok.Getter;

@Getter
public class UpdateProgress {
    private final ProgressType type;
    private final String message;
    private final String currentPlugin;
    private final int current;
    private final int total;

    private UpdateProgress(ProgressType type, String message, String currentPlugin, int current, int total) {
        this.type = type;
        this.message = message;
        this.currentPlugin = currentPlugin;
        this.current = current;
        this.total = total;
    }

    public static UpdateProgress scanning() {
        return new UpdateProgress(ProgressType.SCANNING, "Scanning for updates...", null, 0, 0);
    }

    public static UpdateProgress applying(int total) {
        return new UpdateProgress(ProgressType.APPLYING, "Applying updates...", null, 0, total);
    }

    public static UpdateProgress processing(String plugin, int current, int total) {
        return new UpdateProgress(ProgressType.PROCESSING, "Processing " + plugin, plugin, current, total);
    }

    public static UpdateProgress completed(UpdateResult result) {
        return new UpdateProgress(ProgressType.COMPLETED, "Update completed", null, 0, 0);
    }

    public enum ProgressType {
        SCANNING, APPLYING, PROCESSING, COMPLETED
    }
}
