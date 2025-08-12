package me.kitakeyos.plugin.scheduler;

import lombok.Getter;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ScheduledTask {

    @Getter
    private final long taskId;
    @Getter
    private final boolean repeating;
    @Getter
    private final long createdTime;
    private final ScheduledFuture<?> future;

    public ScheduledTask(long taskId, ScheduledFuture<?> future, boolean repeating) {
        this.taskId = taskId;
        this.future = future;
        this.repeating = repeating;
        this.createdTime = System.currentTimeMillis();
    }

    /**
     * Check if task is cancelled
     */
    public boolean isCancelled() {
        return future.isCancelled();
    }

    /**
     * Check if task is done (completed or cancelled)
     */
    public boolean isDone() {
        return future.isDone();
    }

    /**
     * Get remaining delay until next execution (for scheduled tasks)
     */
    public long getDelay(TimeUnit unit) {
        return future.getDelay(unit);
    }

    /**
     * Cancel the task
     */
    public boolean cancel() {
        return future.cancel(false);
    }

    /**
     * Cancel the task, interrupting if running
     */
    public boolean cancelNow() {
        return future.cancel(true);
    }

    /**
     * Get how long this task has been alive
     */
    public long getAge() {
        return System.currentTimeMillis() - createdTime;
    }

    @Override
    public String toString() {
        return String.format("ScheduledTask{id=%d, repeating=%s, cancelled=%s, done=%s, age=%dms}",
                taskId, repeating, isCancelled(), isDone(), getAge());
    }
}
