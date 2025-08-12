package me.kitakeyos.plugin.scheduler;

import lombok.Getter;

@Getter
public class SchedulerStats {
    private final int activeTasks;
    private final int scheduledActiveThreads;
    private final long scheduledCompletedTasks;
    private final long scheduledTotalTasks;
    private final int asyncActiveThreads;
    private final long asyncCompletedTasks;
    private final long asyncTotalTasks;

    public SchedulerStats(int activeTasks, int scheduledActiveThreads, long scheduledCompletedTasks,
                          long scheduledTotalTasks, int asyncActiveThreads, long asyncCompletedTasks,
                          long asyncTotalTasks) {
        this.activeTasks = activeTasks;
        this.scheduledActiveThreads = scheduledActiveThreads;
        this.scheduledCompletedTasks = scheduledCompletedTasks;
        this.scheduledTotalTasks = scheduledTotalTasks;
        this.asyncActiveThreads = asyncActiveThreads;
        this.asyncCompletedTasks = asyncCompletedTasks;
        this.asyncTotalTasks = asyncTotalTasks;
    }

    @Override
    public String toString() {
        return String.format(
                "SchedulerStats{activeTasks=%d, scheduledPool{active=%d, completed=%d, total=%d}, " +
                        "asyncPool{active=%d, completed=%d, total=%d}}",
                activeTasks, scheduledActiveThreads, scheduledCompletedTasks, scheduledTotalTasks,
                asyncActiveThreads, asyncCompletedTasks, asyncTotalTasks
        );
    }
}
