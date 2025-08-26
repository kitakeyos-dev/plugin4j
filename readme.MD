# Plugin4j

[![JitPack](https://jitpack.io/v/kitakeyos-dev/plugin4j.svg)](https://jitpack.io/#kitakeyos-dev/plugin4j)
[![Java](https://img.shields.io/badge/Java-8%2B-orange)](https://www.oracle.com/java/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Build Status](https://github.com/kitakeyos-dev/plugin4j/workflows/Build%20and%20Release/badge.svg)](https://github.com/kitakeyos-dev/plugin4j/actions)

A comprehensive and extensible plugin management system for Java applications with hot reload capabilities, dependency resolution, and extension points.

## Features

### Core Plugin Management
- **Plugin Lifecycle Management** - Complete plugin loading, enabling, disabling, and unloading
- **Dependency Resolution** - Automatic dependency ordering with circular dependency detection
- **State Management** - Plugin state tracking with validation and transition rules
- **Configuration System** - Per-plugin configuration with Properties support

### Advanced Features
- **Hot Reload** - Live plugin reloading with state preservation
- **Extension Points** - Modular extension system for plugin communication
- **Event System** - Annotation-based event handling with priority support
- **Task Scheduler** - Built-in task scheduling for plugins
- **Standalone Update Management** - Independent plugin update system with flexible options
- **File Watching** - Automatic reload detection when plugin files change

### Safety & Reliability
- **Thread-Safe Operations** - Concurrent plugin operations support
- **Error Recovery** - Graceful error handling and plugin rollback
- **Backup System** - Automatic backups before plugin updates
- **Validation** - Comprehensive plugin validation before loading

## Architecture

```
Plugin4j
├── Core Managers
│   ├── PluginManager (Main orchestrator)
│   ├── PluginRegistry (State management)
│   ├── PluginLoader (JAR loading & class isolation)
│   └── DependencyResolver (Dependency ordering)
├── Extension System
│   └── ExtensionManager (Extension points & extensions)
├── Update System (Standalone)
│   └── PluginUpdateManager (Independent update handling)
├── Hot Reload System
│   ├── HotReloadManager (Live reloading)
│   ├── PluginStateManager (State preservation)
│   └── FileWatcher (Auto-reload detection)
├── Support Systems
│   ├── EventBus (Event handling)
│   ├── TaskScheduler (Scheduled tasks)
│   └── ConfigManager (Plugin configuration)
└── API
    ├── BasePlugin (Plugin base class)
    ├── PluginContext (Plugin services)
    └── Annotations (@Plugin, @Extension, @ExtensionPoint, @EventHandler)
```

## Quick Start

### Maven Dependency

Add JitPack repository and dependency to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.kitakeyos-dev</groupId>
        <artifactId>plugin4j</artifactId>
        <version>v1.0.1</version>
    </dependency>
</dependencies>
```

### Gradle Dependency

Add to your `build.gradle`:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.kitakeyos-dev:plugin4j:v1.0.1'
}
```

### 1. Setup Plugin Manager

```java
// Basic setup (no updates)
File pluginDirectory = new File("plugins");
EventBus eventBus = new EventBus();
PluginManager pluginManager = new PluginManager(pluginDirectory, eventBus);

// Load and enable all plugins
pluginManager.loadAllPlugins();
pluginManager.enableAllPlugins();
```

### 2. Standalone Plugin Updates

```java
// Setup independent update management
File pluginDirectory = new File("plugins");
File updateDirectory = new File("plugin-updates");
PluginLoader loader = new PluginLoader();

// Create update manager with default configuration
PluginUpdateManager updateManager = new PluginUpdateManager(
    pluginDirectory, updateDirectory, loader
);

// Check for available updates
UpdateScanResult scanResult = updateManager.scanForUpdates();
if (scanResult.hasUpdates()) {
    System.out.println("Found " + scanResult.getAvailableUpdates().size() + " updates");
    
    // Apply all updates
    UpdateResult result = updateManager.applyAllUpdates();
    System.out.println("Applied " + result.getUpdatedPlugins().size() + " updates");
}

// Apply specific updates
List<UpdateInfo> selectedUpdates = scanResult.getAvailableUpdates().stream()
    .filter(update -> "MyPlugin".equals(update.getPluginName()))
    .toList();
UpdateResult result = updateManager.applyUpdates(selectedUpdates);
```

### 3. Create a Plugin

```java
@Plugin(
    name = "MyPlugin",
    version = "1.0.0",
    description = "Example plugin",
    author = "Developer",
    dependencies = {"RequiredPlugin"}
)
public class MyPlugin extends BasePlugin {
    
    @Override
    public void onLoad() {
        getContext().getConfig().set("initialized", true);
        System.out.println("Plugin loaded!");
    }
    
    @Override
    public void onEnable() {
        // Schedule repeating task
        getContext().scheduleRepeatingTask(() -> {
            System.out.println("Periodic task running");
        }, 1000, 5000);
        
        System.out.println("Plugin enabled!");
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onCustomEvent(CustomEvent event) {
        System.out.println("Received event: " + event);
    }
    
    @Override
    public void onDisable() {
        System.out.println("Plugin disabled!");
    }
    
    @Override
    public void onUnload() {
        System.out.println("Plugin unloaded!");
    }
}
```

### 4. Plugin Configuration (plugin.ini)

```ini
name=MyPlugin
version=1.0.0
description=Example plugin
author=Developer
main=com.example.MyPlugin
dependencies=RequiredPlugin,AnotherPlugin
```

## Advanced Usage

### Update Management with Custom Configuration

```java
import me.kitakeyos.plugin.config.UpdateConfig;

// Create custom update configuration
UpdateConfig customConfig = new UpdateConfig(
    false,  // Don't check version constraints (force update)
    true,   // Create backups
    true,   // Auto cleanup backups
    true,   // Cleanup update files
    10 * 24 * 60 * 60 * 1000L  // Keep backups for 10 days
);

// Create update manager with custom config
PluginUpdateManager updateManager = new PluginUpdateManager(
    pluginDirectory, updateDirectory, loader, customConfig
);

// Or use predefined configurations
UpdateConfig defaultConfig = UpdateConfig.defaultConfig();       // Safe updates
UpdateConfig forceConfig = UpdateConfig.forceUpdateConfig();     // Force updates
UpdateConfig noBackupConfig = UpdateConfig.noBackupConfig();     // No backups
```

### Async Updates with Progress Monitoring

```java
// Setup progress monitoring
updateManager.setProgressCallback(progress -> {
    switch (progress.getType()) {
        case SCANNING:
            System.out.println("Scanning for updates...");
            break;
        case APPLYING:
            System.out.println("Applying " + progress.getTotal() + " updates...");
            break;
        case PROCESSING:
            System.out.printf("Processing %s (%d/%d)\n", 
                progress.getCurrentPlugin(), 
                progress.getCurrent(), 
                progress.getTotal());
            break;
        case COMPLETED:
            System.out.println("Update completed!");
            break;
    }
});

// Setup log callback
updateManager.setLogCallback(message -> {
    System.out.println("[UPDATE] " + message);
});

// Async update
CompletableFuture<UpdateResult> future = updateManager.applyAllUpdatesAsync();
future.thenAccept(result -> {
    System.out.println("Update finished: " + result);
}).exceptionally(throwable -> {
    System.err.println("Update failed: " + throwable.getMessage());
    return null;
});
```

### Backup Management

```java
// List available backups
List<BackupInfo> backups = updateManager.listBackups();
backups.forEach(backup -> {
    System.out.println("Plugin: " + backup.getPluginName() + 
                      ", Timestamp: " + backup.getTimestamp());
});

// Rollback a plugin
boolean success = updateManager.rollbackPlugin("MyPlugin");
if (success) {
    System.out.println("Plugin rolled back successfully");
} else {
    System.out.println("Rollback failed - no backup found");
}

// Cleanup old backups
int cleaned = updateManager.cleanupOldBackups();
System.out.println("Cleaned up " + cleaned + " old backups");

// Get update statistics
UpdateStats stats = updateManager.getUpdateStats();
System.out.println("Available updates: " + stats.getAvailableUpdates());
System.out.println("Backup count: " + stats.getBackupCount());
```

### Integration Example

```java
public class ApplicationWithPlugins {
    public static void main(String[] args) {
        File pluginDir = new File("plugins");
        File updateDir = new File("updates");
        
        // Initialize components
        EventBus eventBus = new EventBus();
        PluginLoader loader = new PluginLoader();
        PluginManager pluginManager = new PluginManager(pluginDir, eventBus);
        
        // Setup update management
        PluginUpdateManager updateManager = new PluginUpdateManager(
            pluginDir, updateDir, loader, UpdateConfig.defaultConfig()
        );
        
        // Set up progress monitoring
        updateManager.setProgressCallback(progress -> {
            System.out.println("Update progress: " + progress.getMessage());
        });
        
        // Check and apply updates before loading plugins
        System.out.println("Checking for plugin updates...");
        UpdateResult updateResult = updateManager.applyAllUpdates();
        
        if (updateResult.hasUpdates()) {
            System.out.println("Applied " + updateResult.getUpdatedPlugins().size() + " updates");
        }
        
        // Load and enable all plugins
        pluginManager.loadAllPlugins();
        pluginManager.enableAllPlugins();
        
        System.out.println("Application started with " + 
            pluginManager.getLoadedPlugins().size() + " plugins");
        
        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            updateManager.shutdown();
            pluginManager.shutdown();
        }));
    }
}
```

### Hot Reload Setup

```java
// Configure hot reload
HotReloadConfig config = HotReloadConfig.createDevelopment();
HotReloadManager hotReloadManager = new HotReloadManager(pluginManager, config);

// Start automatic file watching
hotReloadManager.startWatching();

// Manual hot reload
CompletableFuture<ReloadResult> result = hotReloadManager.hotReload("MyPlugin");
result.thenAccept(reloadResult -> {
    if (reloadResult.isSuccess()) {
        System.out.println("Hot reload successful!");
    } else {
        System.err.println("Hot reload failed: " + reloadResult.getErrorMessage());
    }
});
```

### Extension Points

```java
// Define extension point
@ExtensionPoint(description = "Command processing extension")
public interface CommandProcessor {
    boolean processCommand(String command, String[] args);
    String getCommandName();
}

// Implement extension in plugin
@Extension(ordinal = 10, description = "Help command processor")
public class HelpCommandProcessor implements CommandProcessor {
    @Override
    public boolean processCommand(String command, String[] args) {
        if ("help".equals(command)) {
            System.out.println("Available commands: help, status, reload");
            return true;
        }
        return false;
    }
    
    @Override
    public String getCommandName() {
        return "help";
    }
}

// Use extensions
List<CommandProcessor> processors = pluginManager.getExtensions(CommandProcessor.class);
for (CommandProcessor processor : processors) {
    if (processor.processCommand("help", new String[]{})) {
        break;
    }
}
```

## Directory Structure

```
project-root/
├── plugins/                    # Plugin JAR files
│   ├── MyPlugin-1.0.0.jar
│   └── AnotherPlugin-2.1.0.jar
├── updates/                    # Plugin updates (for update system)
│   └── MyPlugin-1.1.0.jar
├── plugin-data/                # Plugin configurations & data
│   ├── MyPlugin/
│   │   └── config.properties
│   └── AnotherPlugin/
│       └── config.properties
├── plugin-states/              # Hot reload state snapshots
│   ├── MyPlugin.state
│   └── AnotherPlugin.state
└── plugin-backups/             # Update backups
    └── MyPlugin-20231201-143052-backup.jar
```

## Plugin Update Options

### Update Configurations

```java
// Default configuration (recommended for production)
UpdateConfig.defaultConfig()
    - Version checking: enabled
    - Create backups: enabled  
    - Auto cleanup backups: disabled
    - Cleanup update files: enabled
    - Max backup age: 7 days

// Force update configuration (for development/testing)
UpdateConfig.forceUpdateConfig()
    - Version checking: disabled
    - Create backups: enabled
    - Auto cleanup backups: disabled  
    - Cleanup update files: enabled
    - Max backup age: 7 days

// No backup configuration (for minimal setups)
UpdateConfig.noBackupConfig()
    - Version checking: enabled
    - Create backups: disabled
    - Auto cleanup backups: disabled
    - Cleanup update files: enabled
    - Max backup age: 0 (no backups)
```

### Update Operations

```java
// Scan for updates without applying
UpdateScanResult scan = updateManager.scanForUpdates();

// Apply all available updates
UpdateResult result = updateManager.applyAllUpdates();

// Apply specific updates
List<UpdateInfo> selectedUpdates = scan.getAvailableUpdates().stream()
    .filter(update -> someCondition(update))
    .toList();
UpdateResult result = updateManager.applyUpdates(selectedUpdates);

// Async operations
CompletableFuture<UpdateResult> asyncResult = updateManager.applyUpdatesAsync(selectedUpdates);
```

## Performance Features

- **Lazy Loading** - Plugins loaded only when needed
- **Class Isolation** - Each plugin has isolated class loader
- **Concurrent Operations** - Thread-safe plugin operations
- **Memory Management** - Automatic cleanup of unused resources
- **Caching** - Plugin metadata and configuration caching
- **Independent Update System** - Updates don't interfere with core plugin management

## Error Handling

- **Graceful Degradation** - Failed plugins don't affect others
- **Rollback Support** - Automatic rollback on update failures
- **State Recovery** - Plugin state restoration on reload failures
- **Comprehensive Logging** - Detailed logging for debugging
- **Validation** - Pre-flight validation before operations

## API Reference

### Core Classes

- **`PluginManager`** - Main plugin management interface
- **`BasePlugin`** - Abstract base class for all plugins
- **`PluginContext`** - Provides access to plugin services
- **`PluginMetadata`** - Plugin information and metadata

### Update Classes

- **`PluginUpdateManager`** - Standalone update management
- **`UpdateConfig`** - Update behavior configuration (in `me.kitakeyos.plugin.config`)
- **`UpdateResult`** - Update operation results
- **`UpdateInfo`** - Information about available updates
- **`UpdateScanResult`** - Results from scanning for updates
- **`BackupInfo`** - Information about plugin backups
- **`UpdateStats`** - Statistics about update system

### Annotations

- **`@Plugin`** - Marks main plugin class
- **`@Extension`** - Marks extension implementation
- **`@ExtensionPoint`** - Marks extension point interface
- **`@EventHandler`** - Marks event handler methods

### Interfaces

- **`StatefulPlugin`** - For plugins that need state preservation
- **`HotReloadAware`** - For plugins that support hot reload

## Testing

```java
@Test
public void testPluginLifecycle() {
    // Setup test environment
    File tempDir = Files.createTempDirectory("test-plugins").toFile();
    EventBus eventBus = new EventBus();
    PluginManager manager = new PluginManager(tempDir, eventBus);
    
    // Load test plugin
    manager.loadAllPlugins();
    
    // Verify plugin loaded
    assertTrue(manager.isPluginLoaded("TestPlugin"));
    assertEquals(PluginState.LOADED, 
        manager.getRegistry().getPluginState("TestPlugin"));
    
    // Enable plugin
    manager.enablePlugin("TestPlugin");
    assertTrue(manager.getRegistry().isEnabled("TestPlugin"));
    
    // Cleanup
    manager.shutdown();
}

@Test
public void testPluginUpdates() {
    File pluginDir = Files.createTempDirectory("test-plugins").toFile();
    File updateDir = Files.createTempDirectory("test-updates").toFile();
    PluginLoader loader = new PluginLoader();
    
    PluginUpdateManager updateManager = new PluginUpdateManager(pluginDir, updateDir, loader);
    
    // Test update scan
    UpdateScanResult scan = updateManager.scanForUpdates();
    assertNotNull(scan);
    
    // Test update application
    UpdateResult result = updateManager.applyAllUpdates();
    assertNotNull(result);
    
    updateManager.shutdown();
}
```

## Configuration

### Plugin Configuration

```properties
# Plugin-specific configuration (plugin-data/MyPlugin/config.properties)
plugin.enabled=true
plugin.debug=false
database.url=jdbc:mysql://localhost:3306/mydb
api.timeout=5000
feature.experimental=false
```

### Hot Reload Configuration

```java
HotReloadConfig config = HotReloadConfig.builder()
    .autoReloadEnabled(true)                    // Enable auto-reload
    .fileChangeDebounceMs(1000)                 // Debounce file changes
    .maxConcurrentReloads(3)                    // Max concurrent reloads
    .enableMetrics(true)                        // Collect metrics
    .enableRollback(true)                       // Enable rollback
    .defaultShutdownTimeoutMs(10000)           // Shutdown timeout
    .build();
```

## Monitoring & Metrics

```java
// Get system status
PluginRegistry.RegistryStatus status = pluginManager.getRegistryStatus();
System.out.println("Total plugins: " + status.getTotal());
        System.out.println("Enabled: " + status.getEnabled());
        System.out.println("Failed: " + status.getError());

// Extension point information
Map<String, ExtensionManager.ExtensionPointInfo> extensionInfo =
        pluginManager.getExtensionPointsInfo();

// Update statistics
UpdateStats stats = updateManager.getUpdateStats();
System.out.println("Available updates: " + stats.getAvailableUpdates());
        System.out.println("Backup count: " + stats.getBackupCount());
```

## Contributing

1. Fork the repository
2. Create feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push to branch: `git push origin feature/amazing-feature`
5. Open Pull Request

### Building from Source

```bash
git clone https://github.com/kitakeyos-dev/plugin4j.git
cd plugin4j
mvn clean install
```

## Links

- **JitPack Repository**: https://jitpack.io/#kitakeyos-dev/plugin4j
- **GitHub Repository**: https://github.com/kitakeyos-dev/plugin4j
- **Issues & Bug Reports**: https://github.com/kitakeyos-dev/plugin4j/issues
- **Releases**: https://github.com/kitakeyos-dev/plugin4j/releases

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Java NIO for efficient file watching
- SLF4J for comprehensive logging
- Lombok for reducing boilerplate code
- Jackson for JSON serialization

---

**Built for the Java plugin development community**