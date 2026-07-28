package me.ifair.luahud.render;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public interface RenderTask {
    void draw(DrawContext context, TextRenderer textRenderer, int screenWidth, int screenHeight);

}
