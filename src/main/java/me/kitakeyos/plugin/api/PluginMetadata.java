package me.kitakeyos.plugin.api;

import lombok.Getter;

import java.io.File;
import java.util.List;

@Getter
public class PluginMetadata {
    private final String name;
    private final String version;
    private final String description;
    private final String author;
    private final String mainClass;
    private final List<String> dependencies;
    private final File jarFile;

    public PluginMetadata(String name, String version, String description,
                          String author, String mainClass, List<String> dependencies,
                          File jarFile) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.author = author;
        this.mainClass = mainClass;
        this.dependencies = dependencies;
        this.jarFile = jarFile;
    }

}
