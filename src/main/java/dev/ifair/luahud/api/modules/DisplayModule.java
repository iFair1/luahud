package dev.ifair.luahud.api.modules;

import dev.ifair.luahud.api.LuaApiRegistrar;
import net.minecraft.client.MinecraftClient;

public class DisplayModule {
    private DisplayModule() {}

    public static void register(LuaApiRegistrar api, MinecraftClient client) {
        api.root()
                .namespace("display", display -> display
                        .integer("width", "GUI scaled width", () -> client.getWindow().getScaledWidth())
                        .integer("height", "GUI scaled height", () -> client.getWindow().getScaledHeight())
                        .integer("scale", "GUI Scale", () -> (int) client.getWindow().getScaleFactor())
                        .integer("windowWidth", "Window width", () -> client.getWindow().getWidth())
                        .integer("windowHeight", "Window height", () -> client.getWindow().getHeight())
                        .integer("hz", "Monitor refresh rate", () -> client.getWindow().getRefreshRate())
                        .bool("isF3On", "Whether the debug menu is on", () -> client.getDebugHud().shouldShowDebugHud())
                        .bool("isChatOn", "Whether the chat is on", () -> client.inGameHud.getChatHud().isChatFocused()));
    }
}
