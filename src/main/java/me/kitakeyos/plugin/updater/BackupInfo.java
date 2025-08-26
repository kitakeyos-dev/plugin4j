package me.kitakeyos.plugin.updater;

import lombok.Getter;

import java.io.File;

@Getter
public class BackupInfo {
    private final String pluginName;
    private final String timestamp;
    private final File backupFile;

    public BackupInfo(String pluginName, String timestamp, File backupFile) {
        this.pluginName = pluginName;
        this.timestamp = timestamp;
        this.backupFile = backupFile;
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", pluginName, timestamp);
    }
}
