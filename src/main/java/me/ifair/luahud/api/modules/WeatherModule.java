package me.ifair.luahud.api.modules;

import me.ifair.luahud.api.core.ApiModule;
import me.ifair.luahud.api.core.LazyTable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.luaj.vm2.LuaTable;

public class WeatherModule implements ApiModule {

    private enum Precipitation {
        CLEAR,
        RAINING,
        SNOWING,
        THUNDERING,
    }

    @Override
    public void register(LuaTable lh) {
        LazyTable weather = new LazyTable();

        weather.addBoolean("isRaining", () -> getPrecipitation() == Precipitation.RAINING);
        weather.addBoolean("isSnowing", () -> getPrecipitation() == Precipitation.SNOWING);
        weather.addBoolean("isThundering", () -> getPrecipitation() == Precipitation.THUNDERING);

        lh.set("weather", weather.build());
    }

    private Precipitation getPrecipitation() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.world == null || client.player == null) return Precipitation.CLEAR;
        if (!client.world.isRaining()) return Precipitation.CLEAR;

        BlockPos pos = client.player.getBlockPos();
        Biome.Precipitation precipitation = client.world.getBiome(pos).value().getPrecipitation(pos);

        if (precipitation == Biome.Precipitation.NONE) return Precipitation.CLEAR;
        if (precipitation == Biome.Precipitation.SNOW) return Precipitation.SNOWING;
        if (client.world.isThundering()) return Precipitation.THUNDERING;

        return Precipitation.RAINING;
    }
}
