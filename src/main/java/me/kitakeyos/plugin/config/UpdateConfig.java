package me.kitakeyos.plugin.config;

import lombok.Getter;

@Getter
public class UpdateConfig {
    private final boolean checkVersionConstraints;
    private final boolean createBackups;
    private final boolean autoCleanupBackups;
    private final boolean cleanupUpdateFiles;
    private final long maxBackupAge; // in milliseconds

    public UpdateConfig(boolean checkVersionConstraints, boolean createBackups,
                        boolean autoCleanupBackups, boolean cleanupUpdateFiles, long maxBackupAge) {
        this.checkVersionConstraints = checkVersionConstraints;
        this.createBackups = createBackups;
        this.autoCleanupBackups = autoCleanupBackups;
        this.cleanupUpdateFiles = cleanupUpdateFiles;
        this.maxBackupAge = maxBackupAge;
    }

    public static UpdateConfig defaultConfig() {
        return new UpdateConfig(true, true, false, true, 7 * 24 * 60 * 60 * 1000L); // 7 days
    }

    public static UpdateConfig forceUpdateConfig() {
        return new UpdateConfig(false, true, false, true, 7 * 24 * 60 * 60 * 1000L);
    }

    public static UpdateConfig noBackupConfig() {
        return new UpdateConfig(true, false, false, true, 0);
    }

    @Override
    public String toString() {
        return String.format("UpdateConfig{checkVersion=%b, backup=%b, autoCleanup=%b, cleanup=%b, maxAge=%dms}",
                checkVersionConstraints, createBackups, autoCleanupBackups, cleanupUpdateFiles, maxBackupAge);
    }
}
