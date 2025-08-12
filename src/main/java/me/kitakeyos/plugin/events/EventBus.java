package me.kitakeyos.plugin.events;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.kitakeyos.plugin.api.annotations.EventHandler;
import me.kitakeyos.plugin.api.annotations.EventPriority;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class EventBus {

    private final Map<Class<? extends ApplicationEvent>, List<EventHandlerMethod>> eventHandlers = new ConcurrentHashMap<>();
    private final ExecutorService eventExecutor = Executors.newFixedThreadPool(4);

    public void register(Object listener) {
        Class<?> clazz = listener.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(EventHandler.class)) {
                EventHandler annotation = method.getAnnotation(EventHandler.class);
                Class<?>[] paramTypes = method.getParameterTypes();

                if (paramTypes.length == 1 && ApplicationEvent.class.isAssignableFrom(paramTypes[0])) {
                    Class<? extends ApplicationEvent> eventType = (Class<? extends ApplicationEvent>) paramTypes[0];

                    EventHandlerMethod handlerMethod = new EventHandlerMethod(
                            listener, method, annotation.priority()
                    );

                    eventHandlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                            .add(handlerMethod);

                    // Sort by priority
                    eventHandlers.get(eventType).sort((a, b) ->
                            Integer.compare(b.priority().getValue(), a.priority().getValue()));
                }
            }
        }
    }

    public void unregister(Object listener) {
        eventHandlers.values().forEach(handlers ->
                handlers.removeIf(handler -> handler.listener() == listener));
    }

    public void fireEvent(ApplicationEvent event) {
        List<EventHandlerMethod> handlers = eventHandlers.get(event.getClass());
        if (handlers != null) {
            for (EventHandlerMethod handler : handlers) {
                if (event.isCancelled() && handler.isIgnoreCancelled()) {
                    continue;
                }
                eventExecutor.submit(() -> {
                    try {
                        handler.method().setAccessible(true);
                        handler.method().invoke(handler.listener(), event);
                    } catch (Exception e) {
                        log.error("Error handling event: {}", e.getMessage(), e);
                    }
                });
            }
        }
    }

    public void fireEventSync(ApplicationEvent event) {
        List<EventHandlerMethod> handlers = eventHandlers.get(event.getClass());
        if (handlers != null) {
            for (EventHandlerMethod handler : handlers) {
                if (event.isCancelled() && handler.isIgnoreCancelled()) {
                    continue;
                }
                try {
                    handler.method().setAccessible(true);
                    handler.method().invoke(handler.listener(), event);
                } catch (Exception e) {
                    log.error("Error handling event: {}", e.getMessage(), e);
                }
            }
        }
    }

    public void shutdown() {
        eventExecutor.shutdown();
    }


    private static final class EventHandlerMethod {
        private final Object listener;
        private final Method method;
        private final EventPriority priority;
        @Getter
        private boolean ignoreCancelled;

        private EventHandlerMethod(Object listener, Method method, EventPriority priority) {
            this.listener = listener;
            this.method = method;
            this.priority = priority;
        }

        public Object listener() {
            return listener;
        }

        public Method method() {
            return method;
        }

        public EventPriority priority() {
            return priority;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            EventHandlerMethod that = (EventHandlerMethod) obj;
            return Objects.equals(this.listener, that.listener) &&
                    Objects.equals(this.method, that.method) &&
                    Objects.equals(this.priority, that.priority);
        }

        @Override
        public int hashCode() {
            return Objects.hash(listener, method, priority);
        }

        @Override
        public String toString() {
            return "EventHandlerMethod[" +
                    "listener=" + listener + ", " +
                    "method=" + method + ", " +
                    "priority=" + priority + ']';
        }


        }
}
