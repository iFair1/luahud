package dev.ifair.luahud.api.modules;

import dev.ifair.luahud.api.LuaApiRegistrar;
import net.minecraft.client.MinecraftClient;

public class DisplayModule {
    private DisplayModule() {}

    public static void register(LuaApiRegistrar api, MinecraftClient client) {
        api.root()
                .namespace("display", display -> display
                        .integer("width", "Window width", () -> client.getWindow().getWidth())
                        .integer("height", "Window height", () -> client.getWindow().getHeight())
                        .integer("scale", "GUI Scale", () -> (int) client.getWindow().getScaleFactor())
                        .integer("hz", "Monitor refresh rate", () -> client.getWindow().getRefreshRate())
                        .bool("isF3On", "Whether the debug menu is on", () -> client.getDebugHud().shouldShowDebugHud()));
    }
}
