package me.ifair.luahud;

import me.ifair.luahud.api.core.ApiRegistry;
import me.ifair.luahud.api.core.CoreApi;
import me.ifair.luahud.api.core.StubGenerator;
import me.ifair.luahud.config.ConfigInstaller;
import me.ifair.luahud.config.ConfigManager;
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
        ConfigManager.load();
        ConfigInstaller.generateDefaults();

        ApiRegistry api = new ApiRegistry();
        CoreApi.register(api);
        api.discoverAddons();
        api.freeze();
        try {
            StubGenerator.writeIfChanged(ConfigManager.getScriptsDir().resolve("api/lh.lua"), StubGenerator.generate(api));
        } catch (Exception error) {
            LuaHUD.LOGGER.error("Could not generate config/luahud/api/lh.lua; retaining the previous file", error);
        }

        Engine engine = new Engine(api);
        EventDispatcher.attach(engine);
        TimerManager.attach(engine);
        engine.init();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || !engine.isReady()) return;

            engine.checkForUpdates();
            TimerManager.tick();

            RenderBuffer.clear();
            CoreApi.resetSection();
            EventDispatcher.fireEvent("tick");
        });

        HudRenderCallback.EVENT.register(((drawContext, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();

            if (client.player == null || !engine.isReady()) return; // Return if not in a world

            // Java does the drawing
            int width = client.getWindow().getScaledWidth();
            int height = client.getWindow().getScaledHeight();
            RenderBuffer.renderAll(drawContext, client.textRenderer, width, height);
        }));
    }
}
