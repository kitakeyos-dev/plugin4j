# Plugin4j

[![JitPack](https://jitpack.io/v/kitakeyos-dev/plugin4j.svg)](https://jitpack.io/#kitakeyos-dev/plugin4j)
[![Java](https://img.shields.io/badge/Java-8%2B-orange)](https://www.oracle.com/java/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Build Status](https://github.com/kitakeyos-dev/plugin4j/workflows/Build%20and%20Release/badge.svg)](https://github.com/kitakeyos-dev/plugin4j/actions)

A comprehensive and extensible plugin management system for Java applications with hot reload capabilities, dependency resolution, and extension points.

## ✨ Features

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
- **Update Management** - Automatic plugin updates with rollback capabilities
- **File Watching** - Automatic reload detection when plugin files change

### Safety & Reliability
- **Thread-Safe Operations** - Concurrent plugin operations support
- **Error Recovery** - Graceful error handling and plugin rollback
- **Backup System** - Automatic backups before plugin updates
- **Validation** - Comprehensive plugin validation before loading

## 🏗️ Architecture

```
Plugin4j
├── Core Managers
│   ├── PluginManager (Main orchestrator)
│   ├── PluginRegistry (State management)
│   ├── PluginLoader (JAR loading & class isolation)
│   └── DependencyResolver (Dependency ordering)
├── Extension System
│   └── ExtensionManager (Extension points & extensions)
├── Hot Reload System
│   ├── HotReloadManager (Live reloading)
│   ├── PluginStateManager (State preservation)
│   └── FileWatcher (Auto-reload detection)
├── Support Systems
│   ├── EventBus (Event handling)
│   ├── TaskScheduler (Scheduled tasks)
│   ├── ConfigManager (Plugin configuration)
│   └── PluginUpdateManager (Updates & rollback)
└── API
    ├── BasePlugin (Plugin base class)
    ├── PluginContext (Plugin services)
    └── Annotations (@Plugin, @Extension, @EventHandler)
```

## 🚀 Quick Start

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
        <version>v1.0.0</version>
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
    implementation 'com.github.kitakeyos-dev:plugin4j:v1.0.0'
}
```

### 1. Setup Plugin Manager

```java
// Basic setup
File pluginDirectory = new File("plugins");
EventBus eventBus = new EventBus();
PluginManager pluginManager = new PluginManager(pluginDirectory, eventBus);

// With update support
File updateDirectory = new File("plugin-updates");
PluginManager pluginManager = new PluginManager(
    pluginDirectory, updateDirectory, eventBus
);

// Load and enable all plugins
pluginManager.loadAllPlugins();
pluginManager.enableAllPlugins();
```

### 2. Create a Plugin

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
        // Register event handlers
        getContext().getEventBus().register(this);
        
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
        getContext().getEventBus().unregister(this);
        System.out.println("Plugin disabled!");
    }
    
    @Override
    public void onUnload() {
        System.out.println("Plugin unloaded!");
    }
}
```

### 3. Plugin Configuration (plugin.ini)

```ini
name=MyPlugin
version=1.0.0
description=Example plugin
author=Developer
main=com.example.MyPlugin
dependencies=RequiredPlugin,AnotherPlugin
api-version=1.0
```

## 🔧 Advanced Usage

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

### State Preservation in Hot Reload

```java
public class StatefulPlugin extends BasePlugin implements StatefulPlugin {
    private Map<String, String> inMemoryData = new HashMap<>();
    
    @Override
    public Map<String, Object> saveState() {
        Map<String, Object> state = new HashMap<>();
        state.put("inMemoryData", inMemoryData);
        state.put("lastProcessedTime", System.currentTimeMillis());
        return state;
    }
    
    @Override
    public void loadState(Map<String, Object> state) {
        inMemoryData = (Map<String, String>) state.get("inMemoryData");
        Long lastTime = (Long) state.get("lastProcessedTime");
        System.out.println("Restored state from: " + new Date(lastTime));
    }
}
```

## 📁 Directory Structure

```
project-root/
├── plugins/                    # Plugin JAR files
│   ├── MyPlugin-1.0.0.jar
│   └── AnotherPlugin-2.1.0.jar
├── plugin-updates/             # Plugin updates (optional)
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
    └── MyPlugin-1678901234-backup.jar
```

## 🔄 Plugin Lifecycle

1. **Discovery** - Scan plugin directory for JAR files
2. **Metadata Loading** - Extract plugin.ini or @Plugin annotation
3. **Dependency Resolution** - Sort plugins by dependency order
4. **Loading** - Create plugin instances and call `onLoad()`
5. **Registration** - Register with plugin registry
6. **Enabling** - Call `onEnable()` and register event handlers
7. **Runtime** - Plugin is active and processing events
8. **Disabling** - Call `onDisable()` and cleanup
9. **Unloading** - Call `onUnload()` and remove from registry

## ⚡ Performance Features

- **Lazy Loading** - Plugins loaded only when needed
- **Class Isolation** - Each plugin has isolated class loader
- **Concurrent Operations** - Thread-safe plugin operations
- **Memory Management** - Automatic cleanup of unused resources
- **Caching** - Plugin metadata and configuration caching

## 🛡️ Error Handling

- **Graceful Degradation** - Failed plugins don't affect others
- **Rollback Support** - Automatic rollback on update failures
- **State Recovery** - Plugin state restoration on reload failures
- **Comprehensive Logging** - Detailed logging for debugging
- **Validation** - Pre-flight validation before operations

## 📦 Installation & Usage

### Latest Version

Check the latest version on JitPack: [![](https://jitpack.io/v/kitakeyos-dev/plugin4j.svg)](https://jitpack.io/#kitakeyos-dev/plugin4j)

### Version History

- **v1.0.0** - Initial stable release
- See [Releases](https://github.com/kitakeyos-dev/plugin4j/releases) for full changelog

### Requirements

- Java 8 or higher
- Maven 3.6+ or Gradle 6.0+ (for building from source)

## 🔧 Configuration

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

### Plugin Configuration

```properties
# Plugin-specific configuration (plugin-data/MyPlugin/config.properties)
plugin.enabled=true
plugin.debug=false
database.url=jdbc:mysql://localhost:3306/mydb
api.timeout=5000
feature.experimental=false
```

## 📋 API Reference

### Core Classes

- **`PluginManager`** - Main plugin management interface
- **`BasePlugin`** - Abstract base class for all plugins
- **`PluginContext`** - Provides access to plugin services
- **`PluginMetadata`** - Plugin information and metadata

### Annotations

- **`@Plugin`** - Marks main plugin class
- **`@Extension`** - Marks extension implementation
- **`@ExtensionPoint`** - Marks extension point interface
- **`@EventHandler`** - Marks event handler methods

### Interfaces

- **`StatefulPlugin`** - For plugins that need state preservation
- **`HotReloadAware`** - For plugins that support hot reload

## 🧪 Testing

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
```

## 🚀 Production Deployment

### Recommended Setup

```java
// Production configuration
HotReloadConfig prodConfig = HotReloadConfig.createProduction();
PluginManager pluginManager = new PluginManager(
    new File("/opt/app/plugins"),
    new File("/opt/app/plugin-updates"),
    new EventBus()
);

// Setup with hot reload for production
HotReloadManager hotReloadManager = new HotReloadManager(pluginManager, prodConfig);

// Graceful shutdown
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    hotReloadManager.stopWatching();
    pluginManager.shutdown();
}));
```

### Security Considerations

- Plugin JAR files should be validated before deployment
- Use security manager to restrict plugin permissions
- Monitor plugin resource usage
- Regular security audits of plugin dependencies

## 📊 Monitoring & Metrics

```java
// Get system status
PluginRegistry.RegistryStatus status = pluginManager.getRegistryStatus();
System.out.println("Total plugins: " + status.getTotal());
System.out.println("Enabled: " + status.getEnabled());
System.out.println("Failed: " + status.getError());

// Extension point information
Map<String, ExtensionManager.ExtensionPointInfo> extensionInfo = 
    pluginManager.getExtensionPointsInfo();

// Update results
if (pluginManager.isUpdateSupported()) {
    PluginUpdateManager.UpdateResult result = 
        pluginManager.checkAndApplyUpdates();
    System.out.println("Updates applied: " + result.getUpdatedPlugins().size());
}
```

## 🤝 Contributing

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

### Using Snapshot Versions

To use the latest development version:

```xml
<dependency>
    <groupId>com.github.kitakeyos-dev</groupId>
    <artifactId>plugin4j</artifactId>
    <version>master-SNAPSHOT</version>
</dependency>
```

## 🔗 Links

- **JitPack Repository**: https://jitpack.io/#kitakeyos-dev/plugin4j
- **GitHub Repository**: https://github.com/kitakeyos-dev/plugin4j
- **Issues & Bug Reports**: https://github.com/kitakeyos-dev/plugin4j/issues
- **Releases**: https://github.com/kitakeyos-dev/plugin4j/releases

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Java NIO for efficient file watching
- SLF4J for comprehensive logging
- Lombok for reducing boilerplate code
- Jackson for JSON serialization

---

**Built with ❤️ for the Java plugin development community**