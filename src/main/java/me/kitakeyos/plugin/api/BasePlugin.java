package me.kitakeyos.plugin.api;

import lombok.Getter;
import lombok.Setter;

import java.io.File;

@Setter
public abstract class BasePlugin {

    protected PluginMetadata metadata;
    @Getter
    protected PluginContext context;

    public String getName() {
        return metadata.getName();
    }

    public String getVersion() {
        return metadata.getVersion();
    }

    public String getDescription() {
        return metadata.getDescription();
    }

    public String getAuthor() {
        return metadata.getAuthor();
    }

    public File getJarFile() {
        return metadata.getJarFile();
    }

    /**
     * Called when plugin is loaded
     */
    public abstract void onLoad();

    /**
     * Called when plugin is enabled
     */
    public abstract void onEnable();

    /**
     * Called when plugin is disabled
     */
    public abstract void onDisable();

    /**
     * Called when plugin is unloaded
     */
    public abstract void onUnload();
}
