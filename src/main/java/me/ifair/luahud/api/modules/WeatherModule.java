package me.ifair.luahud.api.modules;

import me.ifair.luahud.api.core.ApiModule;
import me.ifair.luahud.api.core.LazyTable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.luaj.vm2.LuaTable;

public class WeatherModule implements ApiModule {
    @Override
    public void register(LuaTable lh) {
        LazyTable weather = new LazyTable();

        weather.addBoolean("isRaining", () -> {
            ClientWorld world = MinecraftClient.getInstance().world;
            return world != null && world.isRaining();
        });
        weather.addBoolean("isThundering", () -> {
            ClientWorld world = MinecraftClient.getInstance().world;
            return world != null && world.isThundering();
        });

        lh.set("weather", weather.build());
    }
}
