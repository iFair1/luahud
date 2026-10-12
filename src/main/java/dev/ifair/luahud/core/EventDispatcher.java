package dev.ifair.luahud.core;

import dev.ifair.luahud.api.EventType;
import net.minecraft.client.MinecraftClient;

/** Java-side dispatch adapter. Use the public api.Events facade from addons. */
public final class EventDispatcher {
    private static volatile Engine engine;
    private EventDispatcher() {}
    public static void attach(Engine value) { engine = value; }

    public static void fireEvent(String eventName) { fireEvent(eventName, null); }

    public static void fireEvent(String eventName, Object payload) {
        Engine target = engine;
        if (target == null) throw new IllegalStateException("LuaHUD event dispatcher is not initialized");
        target.validateEvent(eventName, payload);
        dispatch(() -> target.fireEvent(eventName, payload));
    }

    public static void fireEvent(EventType<Void> event) { fireEvent(event, null); }

    public static <T> void fireEvent(EventType<T> event, T payload) {
        Engine target = engine;
        if (target == null) throw new IllegalStateException("LuaHUD event dispatcher is not initialized");
        target.validateEvent(event, payload);
        dispatch(() -> target.fireEvent(event, payload));
    }

    private static void dispatch(Runnable publication) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isOnThread()) publication.run();
        else client.execute(publication);
    }
}
