package me.ifair.luahud.core;

import me.ifair.luahud.LuaHUD;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.TwoArgFunction;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventDispatcher {
    private static final Map<String, List<LuaValue>> LISTENERS = new ConcurrentHashMap<>();

    private static final Map<String, Long> ERROR_COOLDOWNS = new ConcurrentHashMap<>();

    public static void registerToLua(LuaTable lh) {
        lh.set("on", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue eventName, LuaValue callback) {
                if (eventName.isstring() && callback.isfunction()) {
                    String event = eventName.checkjstring();

                    LISTENERS.computeIfAbsent(event, k -> new CopyOnWriteArrayList<>()).add(callback);
                }
                return LuaValue.NIL;
            }
        });
    }

    public static void fireEvent(String eventName) {
        List<LuaValue> callbacks = LISTENERS.get(eventName);

        if (callbacks != null) {
            for (LuaValue callback : callbacks) {
                try {
                    callback.call();
                } catch (Exception e) {
                    long now = System.currentTimeMillis();
                    long lastLog = ERROR_COOLDOWNS.getOrDefault(eventName, 0L);

                    if (now - lastLog > 5000) {
                        LuaHUD.LOGGER.error("Lua error on event '{}': {}", eventName, e.getMessage());
                        ERROR_COOLDOWNS.put(eventName, now);
                    }
                }
            }
        }
    }

    public static void clearAll() {
        LISTENERS.clear();
    }
}
