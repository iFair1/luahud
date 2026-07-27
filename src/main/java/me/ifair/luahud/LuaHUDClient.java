package me.ifair.luahud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public class LuaHUDClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register((DrawContext drawContext, RenderTickCounter tickCounter) -> {
            renderCustomHud(drawContext);
        });
    }

    private void renderCustomHud(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null) return; // If player is at main menu

        context.drawText(
                client.textRenderer,
                "Hello World!",
                10,
                10,
                0xFFFFFFFF,
                true
        );

        String fps = client.getCurrentFps() + " FPS";

        context.fill(5, 5, 150, 40, 0x80000000);
        context.drawText(client.textRenderer, fps, 10, 25, 0x00BB00, true);


    }
}
