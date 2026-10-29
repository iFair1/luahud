package dev.ifair.luahud.api.modules;

import dev.ifair.luahud.api.LuaApiRegistrar;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

public final class PerformanceModule {
    private PerformanceModule() {}

    public static void register(LuaApiRegistrar api, MinecraftClient client) {
        api.root().namespace("perf", perf -> perf
                .integer("fps", "Current rendered frames per second", () -> client.getCurrentFps())
                .integer("maxFps", "Fps limit; 260 if infinite", () -> client.options.getMaxFps().getValue())
                .integer("ping", "Player latency in milliseconds", () -> ping(client))
                .number("tps", "Integrated-server ticks per second", () -> tps(client)));
    }

    private static int ping(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return 0;
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return entry == null ? 0 : entry.getLatency();
    }

    private static double tps(MinecraftClient client) {
        if (client.getServer() == null) return 0;
        float mspt = client.getServer().getAverageTickTime();
        return mspt <= 0 ? 0 : Math.min(20, 1000.0 / mspt);
    }
}
