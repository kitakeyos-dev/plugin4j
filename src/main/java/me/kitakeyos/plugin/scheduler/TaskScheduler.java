package me.kitakeyos.plugin.scheduler;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class TaskScheduler {

    private final ScheduledExecutorService scheduledExecutor;
    private final ExecutorService asyncExecutor;
    private final AtomicLong taskIdCounter = new AtomicLong(0);
    private final ConcurrentHashMap<Long, ScheduledTask> activeTasks = new ConcurrentHashMap<>();

    public TaskScheduler() {
        this(4, 8); // Default pool sizes
    }

    public TaskScheduler(int scheduledPoolSize, int asyncPoolSize) {
        this.scheduledExecutor = Executors.newScheduledThreadPool(scheduledPoolSize, new NamedThreadFactory("Scheduler"));
        this.asyncExecutor = Executors.newFixedThreadPool(asyncPoolSize, new NamedThreadFactory("AsyncTask"));
    }

    /**
     * Schedule a task to run once after a delay
     */
    public ScheduledTask schedule(Runnable task, long delayMs) {
        return schedule(task, delayMs, TimeUnit.MILLISECONDS);
    }

    public ScheduledTask schedule(Runnable task, long delay, TimeUnit unit) {
        long taskId = taskIdCounter.incrementAndGet();

        ScheduledFuture<?> future = scheduledExecutor.schedule(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("Error executing scheduled task {}: {}", taskId, e.getMessage());
            } finally {
                activeTasks.remove(taskId);
            }
        }, delay, unit);

        ScheduledTask scheduledTask = new ScheduledTask(taskId, future, false);
        activeTasks.put(taskId, scheduledTask);

        return scheduledTask;
    }

    /**
     * Schedule a task to run repeatedly with fixed delay
     */
    public ScheduledTask scheduleRepeating(Runnable task, long initialDelayMs, long periodMs) {
        return scheduleRepeating(task, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
    }

    public ScheduledTask scheduleRepeating(Runnable task, long initialDelay, long period, TimeUnit unit) {
        long taskId = taskIdCounter.incrementAndGet();

        ScheduledFuture<?> future = scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("Error executing repeating task {}: {}", taskId, e.getMessage());
            }
        }, initialDelay, period, unit);

        ScheduledTask scheduledTask = new ScheduledTask(taskId, future, true);
        activeTasks.put(taskId, scheduledTask);

        return scheduledTask;
    }

    /**
     * Schedule a task to run repeatedly with fixed delay between executions
     */
    public ScheduledTask scheduleWithFixedDelay(Runnable task, long initialDelayMs, long delayMs) {
        return scheduleWithFixedDelay(task, initialDelayMs, delayMs, TimeUnit.MILLISECONDS);
    }

    public ScheduledTask scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        long taskId = taskIdCounter.incrementAndGet();

        ScheduledFuture<?> future = scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("Error executing delayed task {}: {}", taskId, e.getMessage());
            }
        }, initialDelay, delay, unit);

        ScheduledTask scheduledTask = new ScheduledTask(taskId, future, true);
        activeTasks.put(taskId, scheduledTask);

        return scheduledTask;
    }

    /**
     * Run a task asynchronously (immediately in background thread)
     */
    public Future<?> runAsync(Runnable task) {
        return asyncExecutor.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("Error executing async task: {}", e.getMessage());
            }
        });
    }

    /**
     * Run a callable asynchronously and return Future with result
     */
    public <T> Future<T> runAsync(Callable<T> task) {
        return asyncExecutor.submit(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                log.error("Error executing async callable: {}", e.getMessage());
                throw e;
            }
        });
    }

    /**
     * Cancel a specific task
     */
    public boolean cancelTask(long taskId) {
        ScheduledTask task = activeTasks.remove(taskId);
        if (task != null) {
            return task.cancel();
        }
        return false;
    }

    /**
     * Cancel all tasks
     */
    public void cancelAllTasks() {
        activeTasks.values().forEach(ScheduledTask::cancel);
        activeTasks.clear();
    }

    /**
     * Get active task count
     */
    public int getActiveTaskCount() {
        return activeTasks.size();
    }

    /**
     * Get scheduler statistics
     */
    public SchedulerStats getStats() {
        ThreadPoolExecutor scheduledPool = (ThreadPoolExecutor) scheduledExecutor;
        ThreadPoolExecutor asyncPool = (ThreadPoolExecutor) asyncExecutor;

        return new SchedulerStats(
                activeTasks.size(),
                scheduledPool.getActiveCount(),
                scheduledPool.getCompletedTaskCount(),
                scheduledPool.getTaskCount(),
                asyncPool.getActiveCount(),
                asyncPool.getCompletedTaskCount(),
                asyncPool.getTaskCount()
        );
    }

    /**
     * Shutdown the scheduler
     */
    public void shutdown() {
        log.info("Shutting down TaskScheduler...");

        cancelAllTasks();

        scheduledExecutor.shutdown();
        asyncExecutor.shutdown();

        try {
            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduledExecutor.shutdownNow();
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("TaskScheduler shutdown complete");
    }

    /**
     * Force shutdown immediately
     */
    public void shutdownNow() {
        cancelAllTasks();
        scheduledExecutor.shutdownNow();
        asyncExecutor.shutdownNow();
    }

    /**
     * Check if scheduler is shutdown
     */
    public boolean isShutdown() {
        return scheduledExecutor.isShutdown() && asyncExecutor.isShutdown();
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final AtomicLong threadNumber = new AtomicLong(1);
        private final String namePrefix;

        NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix + "-Thread-";
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}