package me.ifair.luahud.api.modules;

import me.ifair.luahud.api.core.ApiModule;
import me.ifair.luahud.api.core.LazyTable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.luaj.vm2.LuaTable;

public class WeatherModule implements ApiModule {
    @Override
    public void register(LuaTable lh) {
        LazyTable weather = new LazyTable();

        weather.addBoolean("raining", this::isRaining);
        weather.addBoolean("snowing", this::isSnowing);
        weather.addBoolean("thundering", this::isThundering);

        lh.set("weather", weather.build());
    }

    private boolean isRaining() {
        return checkPrecipitation(Biome.Precipitation.RAIN);
    }

    private boolean isSnowing() {
        return checkPrecipitation(Biome.Precipitation.SNOW);
    }

    private boolean isThundering() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world != null && client.world.isThundering();
    }

    private boolean checkPrecipitation(Biome.Precipitation expectedType) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.world == null || client.player == null || !client.world.isRaining()) {
            return false;
        }

        BlockPos pos = client.player.getBlockPos();
        Biome.Precipitation currentPrecipitation = client.world.getBiome(pos).value().getPrecipitation(pos);

        return currentPrecipitation == expectedType;
    }
}
