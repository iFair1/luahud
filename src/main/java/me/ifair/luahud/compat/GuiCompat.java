package me.ifair.luahud.compat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Minecraft GUI operations whose signatures differ across supported releases. */
public final class GuiCompat {
    private GuiCompat() {}

    public static void drawTexture(DrawContext context, Identifier texture, int x, int y,
                                   float u, float v, int width, int height, int textureWidth, int textureHeight) {
        //? if <1.21.2 {
        context.drawTexture(texture, x, y, u, v, width, height, textureWidth, textureHeight);
        //?} else if <1.21.6 {
        /*context.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, texture,
                x, y, u, v, width, height, textureWidth, textureHeight);
        *///?} else {
        /*context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, texture,
                x, y, u, v, width, height, textureWidth, textureHeight);
        *///?}
    }

    public static void push(DrawContext context, int x, int y, float scale) {
        //? if <1.21.6 {
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(scale, scale, 1f);
        //?} else {
        /*context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);
        *///?}
    }

    public static void pop(DrawContext context) {
        //? if <1.21.6 {
        context.getMatrices().pop();
        //?} else {
        /*context.getMatrices().popMatrix();
        *///?}
    }
}
