package me.ifair.luahud.core;

import me.ifair.luahud.LuaHUD;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.TwoArgFunction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventDispatcher {
    private static final Map<String, List<LuaValue>> LISTENERS = new HashMap<>();

    public static void registerToLua(LuaTable lh) {
        lh.set("on", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue eventName, LuaValue callback) {
                if (eventName.isstring() && callback.isfunction()) {
                    String event = eventName.checkjstring();

                    LISTENERS.computeIfAbsent(event, k -> new ArrayList<>()).add(callback);
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
                    LuaHUD.LOGGER.error("Lua error on event '{}'! Error: {}", eventName, e.getMessage());
                }
            }
        }
    }

    public static void clearAll() {
        LISTENERS.clear();
    }
}
