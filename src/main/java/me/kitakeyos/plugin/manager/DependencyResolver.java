package me.kitakeyos.plugin.manager;

import lombok.Getter;
import me.kitakeyos.plugin.api.PluginMetadata;
import me.kitakeyos.plugin.exceptions.PluginDependencyException;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Enhanced Dependency Resolver with better error handling and circular dependency detection
 * Responsible for resolving plugin loading order based on dependencies
 * Uses topological sort algorithm to ensure dependencies are loaded first
 */
@Slf4j
public class DependencyResolver {

    /**
     * Resolves and returns list of plugins in correct loading order based on dependencies
     *
     * @param plugins Map containing plugin names and their corresponding metadata
     * @return List of plugin names in loading order (dependencies first)
     * @throws PluginDependencyException if circular dependency or missing dependency is detected
     */
    public List<String> resolveDependencies(Map<String, PluginMetadata> plugins) {
        if (plugins.isEmpty()) {
            return Collections.emptyList();
        }

        log.debug("Resolving dependencies for {} plugins", plugins.size());

        List<String> resolved = new ArrayList<>();
        Set<String> resolving = new HashSet<>();
        Set<String> visited = new HashSet<>();

        // Validate all dependencies exist first
        validateDependencies(plugins);

        // Resolve dependencies for all plugins
        for (String pluginName : plugins.keySet()) {
            if (!visited.contains(pluginName)) {
                resolveDependency(pluginName, plugins, resolved, resolving, visited, new ArrayDeque<>());
            }
        }

        log.debug("Dependency resolution completed. Loading order: {}", resolved);
        return resolved;
    }

    /**
     * Validates that all required dependencies exist
     *
     * @param plugins Map of all available plugins
     * @throws PluginDependencyException if any dependency is missing
     */
    private void validateDependencies(Map<String, PluginMetadata> plugins) {
        for (Map.Entry<String, PluginMetadata> entry : plugins.entrySet()) {
            String pluginName = entry.getKey();
            PluginMetadata metadata = entry.getValue();

            for (String dependency : metadata.getDependencies()) {
                if (!plugins.containsKey(dependency)) {
                    throw new PluginDependencyException(
                            pluginName,
                            dependency,
                            "Required dependency not found"
                    );
                }
            }
        }
    }

    /**
     * Recursively resolves dependencies for a specific plugin using DFS with cycle detection
     *
     * @param pluginName    Name of the plugin to resolve
     * @param plugins       Map of all available plugins
     * @param resolved      List of already resolved plugins
     * @param resolving     Set of plugins currently being resolved (for circular dependency detection)
     * @param visited       Set of plugins that have been processed
     * @param pathStack     Stack to track the current dependency path for better error reporting
     * @throws PluginDependencyException if circular dependency is detected
     */
    private void resolveDependency(String pluginName, Map<String, PluginMetadata> plugins,
                                   List<String> resolved, Set<String> resolving, Set<String> visited,
                                   Deque<String> pathStack) {

        // Skip if already resolved
        if (resolved.contains(pluginName)) {
            return;
        }

        // Mark as visited to avoid reprocessing
        visited.add(pluginName);

        // Check for circular dependency
        if (resolving.contains(pluginName)) {
            pathStack.addLast(pluginName);
            List<String> cyclePath = new ArrayList<>(pathStack);
            throw new PluginDependencyException(
                    pluginName,
                    String.join(" -> ", cyclePath),
                    "Circular dependency detected in path: " + String.join(" -> ", cyclePath)
            );
        }

        // Get plugin metadata
        PluginMetadata metadata = plugins.get(pluginName);
        if (metadata == null) {
            throw new PluginDependencyException(
                    pluginName,
                    pluginName,
                    "Plugin metadata not found"
            );
        }

        // Mark as currently resolving and add to path
        resolving.add(pluginName);
        pathStack.addLast(pluginName);

        try {
            // Resolve all dependencies first
            for (String dependency : metadata.getDependencies()) {
                if (!resolved.contains(dependency)) {
                    log.debug("Resolving dependency: {} -> {}", pluginName, dependency);
                    resolveDependency(dependency, plugins, resolved, resolving, visited, pathStack);
                }
            }

            // All dependencies resolved, add this plugin to resolved list
            resolved.add(pluginName);
            log.debug("Resolved plugin: {} (dependencies: {})", pluginName, metadata.getDependencies());

        } finally {
            // Clean up resolving state
            resolving.remove(pluginName);
            pathStack.removeLast();
        }
    }

    /**
     * Analyzes dependency graph and returns detailed information
     *
     * @param plugins Map of all plugins and their metadata
     * @return DependencyAnalysis containing graph information
     */
    public DependencyAnalysis analyzeDependencies(Map<String, PluginMetadata> plugins) {
        Map<String, Set<String>> dependencyGraph = new HashMap<>();
        Map<String, Set<String>> reverseDependencyGraph = new HashMap<>();
        Set<String> rootPlugins = new HashSet<>();
        Set<String> leafPlugins = new HashSet<>();

        // Build dependency graphs
        for (Map.Entry<String, PluginMetadata> entry : plugins.entrySet()) {
            String pluginName = entry.getKey();
            List<String> dependencies = entry.getValue().getDependencies();

            dependencyGraph.put(pluginName, new HashSet<>(dependencies));

            if (dependencies.isEmpty()) {
                rootPlugins.add(pluginName);
            }

            for (String dependency : dependencies) {
                reverseDependencyGraph.computeIfAbsent(dependency, k -> new HashSet<>()).add(pluginName);
            }
        }

        // Find leaf plugins (no other plugins depend on them)
        for (String pluginName : plugins.keySet()) {
            if (!reverseDependencyGraph.containsKey(pluginName)) {
                leafPlugins.add(pluginName);
            }
        }

        return new DependencyAnalysis(
                dependencyGraph,
                reverseDependencyGraph,
                rootPlugins,
                leafPlugins,
                plugins.size()
        );
    }

    /**
     * Checks if the dependency graph has any circular dependencies
     *
     * @param plugins Map of all plugins and their metadata
     * @return List of circular dependency chains found
     */
    public List<List<String>> findCircularDependencies(Map<String, PluginMetadata> plugins) {
        List<List<String>> cycles = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String pluginName : plugins.keySet()) {
            if (!visited.contains(pluginName)) {
                List<String> currentPath = new ArrayList<>();
                findCyclesInGraph(pluginName, plugins, visited, recursionStack, currentPath, cycles);
            }
        }

        return cycles;
    }

    /**
     * DFS helper method to find cycles in the dependency graph
     */
    private void findCyclesInGraph(String pluginName, Map<String, PluginMetadata> plugins,
                                   Set<String> visited, Set<String> recursionStack,
                                   List<String> currentPath, List<List<String>> cycles) {

        visited.add(pluginName);
        recursionStack.add(pluginName);
        currentPath.add(pluginName);

        PluginMetadata metadata = plugins.get(pluginName);
        if (metadata != null) {
            for (String dependency : metadata.getDependencies()) {
                if (!visited.contains(dependency)) {
                    findCyclesInGraph(dependency, plugins, visited, recursionStack, currentPath, cycles);
                } else if (recursionStack.contains(dependency)) {
                    // Found a cycle - extract the cycle path
                    int cycleStart = currentPath.indexOf(dependency);
                    List<String> cycle = new ArrayList<>(currentPath.subList(cycleStart, currentPath.size()));
                    cycle.add(dependency); // Complete the cycle
                    cycles.add(cycle);
                }
            }
        }

        recursionStack.remove(pluginName);
        currentPath.remove(currentPath.size() - 1);
    }

    /**
     * Data class containing dependency analysis results
     */
    @Getter
    public static class DependencyAnalysis {
        private final Map<String, Set<String>> dependencyGraph;
        private final Map<String, Set<String>> reverseDependencyGraph;
        private final Set<String> rootPlugins;
        private final Set<String> leafPlugins;
        private final int totalPlugins;

        public DependencyAnalysis(Map<String, Set<String>> dependencyGraph,
                                  Map<String, Set<String>> reverseDependencyGraph,
                                  Set<String> rootPlugins,
                                  Set<String> leafPlugins,
                                  int totalPlugins) {
            this.dependencyGraph = Collections.unmodifiableMap(new HashMap<>(dependencyGraph));
            this.reverseDependencyGraph = Collections.unmodifiableMap(new HashMap<>(reverseDependencyGraph));
            this.rootPlugins = Collections.unmodifiableSet(new HashSet<>(rootPlugins));
            this.leafPlugins = Collections.unmodifiableSet(new HashSet<>(leafPlugins));
            this.totalPlugins = totalPlugins;
        }

        public Set<String> getPluginsThatDependOn(String pluginName) {
            return reverseDependencyGraph.getOrDefault(pluginName, Collections.emptySet());
        }

        public Set<String> getDependenciesOf(String pluginName) {
            return dependencyGraph.getOrDefault(pluginName, Collections.emptySet());
        }

        public boolean hasCircularDependencies() {
            // This is a simplified check - for full validation use findCircularDependencies
            return dependencyGraph.values().stream().anyMatch(deps -> !deps.isEmpty()) &&
                    rootPlugins.isEmpty();
        }

        @Override
        public String toString() {
            return String.format("DependencyAnalysis{total=%d, roots=%d, leaves=%d, hasCircular=%s}",
                    totalPlugins, rootPlugins.size(), leafPlugins.size(), hasCircularDependencies());
        }
    }
}