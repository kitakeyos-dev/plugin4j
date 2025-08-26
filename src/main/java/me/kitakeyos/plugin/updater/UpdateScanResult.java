package me.kitakeyos.plugin.updater;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class UpdateScanResult {
    private final List<UpdateInfo> availableUpdates;
    private final List<String> invalidFiles;

    public UpdateScanResult(List<UpdateInfo> availableUpdates, List<String> invalidFiles) {
        this.availableUpdates = Collections.unmodifiableList(availableUpdates);
        this.invalidFiles = Collections.unmodifiableList(invalidFiles);
    }

    public boolean hasUpdates() {
        return !availableUpdates.isEmpty();
    }

    public boolean hasInvalidFiles() {
        return !invalidFiles.isEmpty();
    }

    public static UpdateScanResult noUpdates() {
        return new UpdateScanResult(Collections.emptyList(), Collections.emptyList());
    }
}