package dev.ifair.luahud.api.modules;

import dev.ifair.luahud.api.LuaApiRegistrar;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.biome.Biome;

public final class WeatherModule {
    private WeatherModule() {}

    public static void register(LuaApiRegistrar api, MinecraftClient client) {
        api.root().namespace("weather", weather -> weather
                .bool("raining", "Whether rain falls at the player", () -> precipitation(client, Biome.Precipitation.RAIN))
                .bool("snowing", "Whether snow falls at the player", () -> precipitation(client, Biome.Precipitation.SNOW))
                .bool("thundering", "Whether the world is thundering", () -> client.world.isThundering()));
    }

    private static boolean precipitation(MinecraftClient client, Biome.Precipitation expected) {
        return client.world.isRaining()
                //? if <1.21.2 {
                && client.world.getBiome(client.player.getBlockPos()).value().getPrecipitation(client.player.getBlockPos()) == expected;
                //?} else {
                /*&& client.world.getBiome(client.player.getBlockPos()).value().getPrecipitation(client.player.getBlockPos(), client.world.getSeaLevel()) == expected;
                *///?}
    }
}
