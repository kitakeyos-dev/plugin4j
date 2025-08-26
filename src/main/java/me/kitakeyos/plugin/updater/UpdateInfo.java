package me.kitakeyos.plugin.updater;

import lombok.Getter;

import java.io.File;

@Getter
public class UpdateInfo {
    private final String pluginName;
    private final String currentVersion;
    private final String newVersion;
    private final File updateFile;
    private final UpdateType type;

    public UpdateInfo(String pluginName, String currentVersion, String newVersion,
                      File updateFile, UpdateType type) {
        this.pluginName = pluginName;
        this.currentVersion = currentVersion;
        this.newVersion = newVersion;
        this.updateFile = updateFile;
        this.type = type;
    }

    @Override
    public String toString() {
        return String.format("%s: %s -> %s (%s)", pluginName,
                currentVersion != null ? currentVersion : "new",
                newVersion, type);
    }
}
