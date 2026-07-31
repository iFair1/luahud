package me.ifair.luahud.api;

import net.minecraft.client.MinecraftClient;
import org.apache.logging.log4j.util.Lazy;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.TwoArgFunction;

public class PerformanceModule implements ApiModule {
    @Override
    public void register(LuaTable lh) {
        LazyTable performance = new LazyTable();

        performance.addInt("fps", () -> MinecraftClient.getInstance().getCurrentFps());

        lh.set("performance", performance.build());
    }
}