package me.ifair.luahud.api;

import net.minecraft.client.MinecraftClient;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

public class PerformanceModule implements ApiModule {
    @Override
    public void register(LuaTable lh) {
        LuaTable performanceTable = new LuaTable();
        lh.set("performance", performanceTable);
        registerMethods(performanceTable);
    }

    private static void registerMethods(LuaTable table) {
        table.set("getFps", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                int fps = MinecraftClient.getInstance().getCurrentFps();
                return LuaValue.valueOf(fps);
            }
        });
    }
}
