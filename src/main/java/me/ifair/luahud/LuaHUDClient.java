package me.ifair.luahud;

import me.ifair.luahud.api.WorldModule;
import me.ifair.luahud.core.Engine;
import me.ifair.luahud.core.EventDispatcher;
import me.ifair.luahud.core.TimerManager;
import me.ifair.luahud.render.RenderBuffer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

public class LuaHUDClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Engine engine = new Engine();
        engine.init();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || Engine.getLhTable() == null) return; // return early if not in world or LhTable is null

            // Check for Update
            engine.checkForUpdates();

            TimerManager.tick();
            EventDispatcher.fireEvent("tick");
        });

        HudRenderCallback.EVENT.register(((drawContext, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();

            if (client.player == null || Engine.getLhTable() == null) return;

            RenderBuffer.clear();
            EventDispatcher.fireEvent("render");

            int width = client.getWindow().getScaledWidth();
            int height = client.getWindow().getScaledHeight();

            RenderBuffer.renderAll(drawContext, client.textRenderer, width, height);
        }));
    }
}