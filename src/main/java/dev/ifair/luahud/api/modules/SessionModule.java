package dev.ifair.luahud.api.modules;

import dev.ifair.luahud.api.LuaApiRegistrar;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

public class SessionModule {
    private SessionModule() {}

    public static void register(LuaApiRegistrar api, MinecraftClient client) {
        api.root().namespace("session", session -> session
                .bool("multiplayer", "Whether it's multiplayer", () -> isMultiplayer(client))
                .string("name", "Name of the world or server", () -> {
                    if (isMultiplayer(client)) {
                        return getCurrentServer(client).name;
                    }
                    return client.getServer().getSaveProperties().getLevelName();
                })
                .string("ip", "IP address of the current server", () -> {
                    if (!isMultiplayer(client)) return "";

                    return getCurrentServer(client).address;
                })
                );
    }

    private static Boolean isMultiplayer(MinecraftClient client) {
        return !client.isInSingleplayer();
    }

    private static ServerInfo getCurrentServer(MinecraftClient client) {
        return client.getCurrentServerEntry();
    }
}
