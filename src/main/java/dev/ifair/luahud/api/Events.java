package dev.ifair.luahud.api;

import dev.ifair.luahud.core.EventDispatcher;

/**
 * Publishes registered events. Calls from other threads are scheduled on the client thread.
 * Payloads should be immutable; a publication may be delivered after this method returns.
 */
public final class Events {
    private Events() {}

    /** Publishes a registered event without a payload. */
    public static void fireEvent(String name) { EventDispatcher.fireEvent(name); }

    /** Publishes one payload, validated against the registered event's type. */
    public static void fireEvent(String name, Object payload) { EventDispatcher.fireEvent(name, payload); }

    /** Publishes a parameterless event using its registry handle. */
    public static void fireEvent(EventType<Void> event) { EventDispatcher.fireEvent(event); }

    /** Publishes one typed payload using the handle returned by the registrar. */
    public static <T> void fireEvent(EventType<T> event, T payload) { EventDispatcher.fireEvent(event, payload); }
}
