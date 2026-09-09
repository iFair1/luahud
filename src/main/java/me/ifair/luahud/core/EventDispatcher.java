package me.ifair.luahud.core;

/** Java-side facade; CobaltRuntime owns all callback objects. */
public final class EventDispatcher {
    private static Engine engine;
    private EventDispatcher() {}
    public static void attach(Engine value) { engine = value; }
    public static void fireEvent(String eventName) { if (engine != null) engine.fireEvent(eventName); }
    public static void clearAll() { }
}
