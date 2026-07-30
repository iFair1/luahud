package me.ifair.luahud.api;

import net.minecraft.client.MinecraftClient;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.TwoArgFunction;

public class PerformanceModule implements ApiModule {
    @Override
    public void register(LuaTable lh) {
        LuaTable performanceTable = new LuaTable();
        LuaTable metatable = new LuaTable();

        metatable.set(LuaValue.INDEX, new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue table, LuaValue key) {
                if (!key.isstring()) return LuaValue.NIL;

                String varName = key.checkjstring();

                if ("fps".equals(varName)) {
                    int fps = MinecraftClient.getInstance().getCurrentFps();
                    return LuaValue.valueOf(fps);
                }

                return LuaValue.NIL;
            }
        });

        performanceTable.setmetatable(metatable);
        lh.set("performance", performanceTable);
    }
}