package me.ifair.luahud.api;

import me.ifair.luahud.api.core.LazyTable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.server.MinecraftServer;
import org.luaj.vm2.LuaTable;


public class PerformanceModule implements ApiModule {
    @Override
    public void register(LuaTable lh) {
        MinecraftClient client = MinecraftClient.getInstance();

        LazyTable performance = new LazyTable();
        performance.addInt("fps", client::getCurrentFps);
        performance.addInt("ping", this::getPing);
        performance.addDouble("tps", this::getTps);

        lh.set("perf", performance.build());
    }

    private int getPing() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null || client.getNetworkHandler() == null) {
            return 0;
        }

        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return entry != null ? entry.getLatency() : 0;
    }

    private double getTps() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client == null || client.getServer() == null) {
            return 0.0;
        }

        MinecraftServer server = client.getServer();
        float mspt = server.getAverageTickTime();

        if (mspt <= 0) {
            return 0.0;
        }

        double tps = 1000.0 / mspt;

        return Math.min(20.0, tps);
    }
}