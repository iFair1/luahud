package me.ifair.luahud.api.modules;

import me.ifair.luahud.api.LuaApiRegistrar;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

public final class PositionModule {
    private PositionModule() {}

    public static void register(LuaApiRegistrar api, MinecraftClient client) {
        api.root().namespace("pos", pos -> {
            pos.namespace("biome", biome -> biome
                    .string("id", "Namespaced biome ID", () -> client.world == null || client.player == null
                            ? "" : client.world.getBiome(client.player.getBlockPos()).getIdAsString())
                    .string("name", "Localized biome name", () -> biomeName(client)));
            pos.namespace("dimension", dimension -> dimension.string("id", "Namespaced dimension ID",
                    () -> client.world == null ? "" : client.world.getRegistryKey().getValue().toString()));
        });
    }

    private static String biomeName(MinecraftClient client) {
        if (client.world == null || client.player == null) return "";
        return client.world.getBiome(client.player.getBlockPos()).getKey()
                .map(key -> Text.translatable(Util.createTranslationKey("biome", key.getValue())).getString())
                .orElse("");
    }
}
