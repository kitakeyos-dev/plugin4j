package me.kitakeyos.plugin.reload;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Monitors file system changes for plugin JAR files
 * Uses Java NIO WatchService for efficient file monitoring with checksum validation
 */
@Slf4j
public class FileWatcher {

    private final Path watchDirectory;
    private final Consumer<Path> changeCallback;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean watching = new AtomicBoolean(false);
    private final ConcurrentHashMap<Path, FileInfo> fileStates = new ConcurrentHashMap<>();

    private WatchService watchService;
    private Thread watchThread;

    /**
     * Creates a new file watcher for the specified directory
     *
     * @param watchDirectory Directory to monitor for file changes
     * @param changeCallback Callback invoked when file changes are detected
     */
    public FileWatcher(File watchDirectory, Consumer<Path> changeCallback) {
        this.watchDirectory = watchDirectory.toPath();
        this.changeCallback = changeCallback;
        this.executor = Executors.newScheduledThreadPool(2);

        // Initialize tracking for existing files
        scanInitialFiles();
    }

    /**
     * Starts file monitoring
     * Registers watch service for directory events and begins monitoring thread
     *
     * @throws RuntimeException if watch service cannot be started
     */
    public void start() {
        if (watching.compareAndSet(false, true)) {
            try {
                watchService = FileSystems.getDefault().newWatchService();
                watchDirectory.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);

                watchThread = new Thread(this::watchLoop, "FileWatcher-" + watchDirectory.getFileName());
                watchThread.setDaemon(true);
                watchThread.start();

                // Start periodic checksum validation to catch missed changes
                executor.scheduleWithFixedDelay(this::validateChecksums, 10, 30, TimeUnit.SECONDS);

                log.info("File watcher started for directory: {}", watchDirectory);

            } catch (IOException e) {
                watching.set(false);
                throw new RuntimeException("Failed to start file watcher", e);
            }
        }
    }

    /**
     * Stops file monitoring and releases resources
     * Gracefully shuts down watch service and executor threads
     */
    public void stop() {
        if (watching.compareAndSet(true, false)) {
            try {
                if (watchService != null) {
                    watchService.close();
                }
                if (watchThread != null) {
                    watchThread.interrupt();
                }
                executor.shutdown();

                log.info("File watcher stopped");

            } catch (IOException e) {
                log.warn("Error stopping file watcher: {}", e.getMessage());
            }
        }
    }

    /**
     * Main watch loop that processes file system events
     * Runs in separate thread to avoid blocking the main application
     */
    private void watchLoop() {
        while (watching.get() && !Thread.currentThread().isInterrupted()) {
            try {
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        log.warn("File watcher overflow - some events may have been lost");
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path fileName = pathEvent.context();
                    Path fullPath = watchDirectory.resolve(fileName);

                    handleFileEvent(kind, fullPath);
                }

                boolean valid = key.reset();
                if (!valid) {
                    log.warn("Watch key no longer valid, stopping watcher");
                    break;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in file watch loop: {}", e.getMessage());
            }
        }
    }

    /**
     * Handles individual file system events
     * Filters for relevant file types and debounces rapid changes
     *
     * @param kind     Type of file system event
     * @param filePath Path of the affected file
     */
    private void handleFileEvent(WatchEvent.Kind<?> kind, Path filePath) {
        if (!isWatchedFile(filePath)) {
            return;
        }

        log.debug("File event: {} for {}", kind, filePath);

        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            fileStates.remove(filePath);
            log.info("File deleted: {}", filePath);
            return;
        }

        // Handle create/modify events with debouncing
        executor.schedule(() -> {
            try {
                if (Files.exists(filePath)) {
                    FileInfo oldInfo = fileStates.get(filePath);
                    FileInfo newInfo = createFileInfo(filePath);

                    if (oldInfo == null || !oldInfo.equals(newInfo)) {
                        fileStates.put(filePath, newInfo);

                        log.info("File changed detected: {}", filePath);
                        changeCallback.accept(filePath);
                    }
                }
            } catch (Exception e) {
                log.warn("Error processing file change for {}: {}", filePath, e.getMessage());
            }
        }, 100, TimeUnit.MILLISECONDS); // Small delay to handle rapid successive writes
    }

    /**
     * Checks if a file should be monitored
     * Currently monitors .jar and .zip files
     *
     * @param filePath Path to check
     * @return true if file should be monitored
     */
    private boolean isWatchedFile(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        return fileName.endsWith(".jar") || fileName.endsWith(".zip");
    }

    /**
     * Scans directory for existing files and initializes tracking
     * Called during startup to establish baseline file states
     */
    private void scanInitialFiles() {
        try {
            if (Files.exists(watchDirectory)) {
                Files.list(watchDirectory)
                        .filter(this::isWatchedFile)
                        .forEach(path -> {
                            try {
                                fileStates.put(path, createFileInfo(path));
                            } catch (Exception e) {
                                log.warn("Error scanning initial file {}: {}", path, e.getMessage());
                            }
                        });
            }
        } catch (IOException e) {
            log.warn("Error scanning initial files: {}", e.getMessage());
        }
    }

    /**
     * Periodically validates file checksums to catch missed changes
     * Removes deleted files and detects modifications not caught by watch service
     */
    private void validateChecksums() {
        fileStates.entrySet().removeIf(entry -> {
            Path path = entry.getKey();
            FileInfo storedInfo = entry.getValue();

            try {
                if (!Files.exists(path)) {
                    log.info("File no longer exists, removing from tracking: {}", path);
                    return true;
                }

                FileInfo currentInfo = createFileInfo(path);
                if (!storedInfo.equals(currentInfo)) {
                    log.info("File checksum changed: {}", path);
                    fileStates.put(path, currentInfo);
                    changeCallback.accept(path);
                }

                return false;
            } catch (Exception e) {
                log.warn("Error validating checksum for {}: {}", path, e.getMessage());
                return false;
            }
        });
    }

    /**
     * Creates file information for change detection
     * Uses size and modification time for efficient comparison
     *
     * @param filePath Path to analyze
     * @return FileInfo containing size, modification time, and checksum
     * @throws IOException if file cannot be read
     */
    private FileInfo createFileInfo(Path filePath) throws IOException {
        long size = Files.size(filePath);
        long lastModified = Files.getLastModifiedTime(filePath).toMillis();

        // Simple checksum based on size and modification time
        // For production, consider using actual file hash (MD5/SHA) for better accuracy
        long checksum = size ^ lastModified;

        return new FileInfo(size, lastModified, checksum);
    }

    /**
     * Container for file metadata used in change detection
     */
    private static class FileInfo {
        private final long size;
        private final long lastModified;
        private final long checksum;

        /**
         * Creates file information record
         *
         * @param size         File size in bytes
         * @param lastModified Last modification timestamp
         * @param checksum     Computed checksum for change detection
         */
        public FileInfo(long size, long lastModified, long checksum) {
            this.size = size;
            this.lastModified = lastModified;
            this.checksum = checksum;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;

            FileInfo fileInfo = (FileInfo) obj;
            return size == fileInfo.size &&
                    lastModified == fileInfo.lastModified &&
                    checksum == fileInfo.checksum;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(size) ^ Long.hashCode(lastModified) ^ Long.hashCode(checksum);
        }

        @Override
        public String toString() {
            return String.format("FileInfo{size=%d, lastModified=%d, checksum=%d}",
                    size, lastModified, checksum);
        }
    }
}