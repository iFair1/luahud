package me.ifair.luahud.core;

import me.ifair.luahud.LuaHUD;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ThreeArgFunction;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TimerManager {
    private static class TimerTask {
        int interval;
        int currentTick = 0;
        LuaValue callback;

        TimerTask(int interval, LuaValue callback) {
            this.interval = interval;
            this.callback = callback;
        }
    }

    private static final List<TimerTask> TASKS = new CopyOnWriteArrayList<>();

    public static void registerToLua(LuaTable lh) {
        lh.set("setInterval", new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue time, LuaValue type, LuaValue callback) {
                // For now, only ticks is supported
                if (type.checkjstring().equals("ticks") && callback.isfunction())  {
                    TASKS.add(new TimerTask(time.checkint(), callback));
                }
                return LuaValue.NIL;
            }
        });
    }

    public static void tick() {
        for (TimerTask task : TASKS) {
            task.currentTick++;
            if (task.currentTick >= task.interval) {
                task.currentTick = 0;
                try {
                    task.callback.call();
                } catch (Exception e) {
                    LuaHUD.LOGGER.error("Interval error: {}", e.getMessage());
                }
            }
        }
    }

    public static void clearAll() {
        TASKS.clear();
    }
}
