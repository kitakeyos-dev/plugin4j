package me.kitakeyos.plugin.events;

import lombok.Getter;
import lombok.Setter;

public abstract class ApplicationEvent {
    private final String eventType;
    private final long timestamp;
    @Setter
    @Getter
    private boolean cancelled = false;

    public ApplicationEvent(String eventType) {
        this.eventType = eventType;
        this.timestamp = System.currentTimeMillis();
    }
}
