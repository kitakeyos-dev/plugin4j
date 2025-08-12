package me.kitakeyos.plugin.api;


import lombok.Getter;
import me.kitakeyos.plugin.config.PluginConfig;
import me.kitakeyos.plugin.events.EventBus;
import me.kitakeyos.plugin.scheduler.ScheduledTask;
import me.kitakeyos.plugin.scheduler.TaskScheduler;

@Getter
public class PluginContext {
    private final String pluginName;
    private final EventBus eventBus;
    private final TaskScheduler scheduler;
    private final PluginConfig config;

    public PluginContext(String pluginName, EventBus eventBus, TaskScheduler scheduler, PluginConfig config) {
        this.pluginName = pluginName;
        this.eventBus = eventBus;
        this.scheduler = scheduler;
        this.config = config;
    }

    public ScheduledTask scheduleTask(Runnable task, long delayMs) {
        return scheduler.schedule(task, delayMs);
    }

    public ScheduledTask scheduleRepeatingTask(Runnable task, long delayMs, long periodMs) {
        return scheduler.scheduleRepeating(task, delayMs, periodMs);
    }
}
