package me.ifair.luahud.core;

/** Java-side facade; CobaltRuntime owns all timer callback objects. */
public final class TimerManager {
    private static Engine engine;
    private TimerManager() {}
    public static void attach(Engine value) { engine = value; }
    public static void tick() { if (engine != null) engine.tickTimers(); }
    public static void clearAll() { }
}
