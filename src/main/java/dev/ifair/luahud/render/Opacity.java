package dev.ifair.luahud.render;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;

/** Render-thread scope, also captured by the deferred GUI renderer. */
public final class Opacity {
    private static float current = 1;
    private static boolean writingVertices;
    public static void writingVertices(boolean value) { writingVertices = value; }
    public static int vertexAlpha(int alpha) { return writingVertices ? alpha(alpha) : alpha; }
    public static int vertexColor(int color) { return writingVertices ? color(color) : color; }
    private static final Deque<float[]> shaderColors = new ArrayDeque<>();
    private static final Deque<Float> scopes = new ArrayDeque<>();
    private static final Map<Object, Float> states = new IdentityHashMap<>();
    private Opacity() {}
    public static float current() { return current; }
    public static int alpha(int alpha) { return Math.round(alpha * current); }
    public static int color(int color) { return (color & 0xFFFFFF) | alpha(color >>> 24) << 24; }
    public static void capture(Object state) { if (current != 1) states.put(state, current); }
    public static void prepare(Object state) { push(states.getOrDefault(state, 1f)); }
    public static void clear() { states.clear(); }
    public static void push(float opacity) { scopes.push(current); current = opacity; }
    public static void pop() { current = scopes.pop(); }
    public static void begin(DrawContext context, float opacity) {
        //? if <1.21.6 {
        context.draw();
        float[] color = com.mojang.blaze3d.systems.RenderSystem.getShaderColor().clone();
        shaderColors.push(color);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(color[0], color[1], color[2], color[3] * opacity);
        //?}
        push(opacity);
        //? if <1.21.5 {
        if (opacity < 1) {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        }
        //?}
    }
    public static void end(DrawContext context) {
        //? if <1.21.6 {
        context.draw();
        float[] color = shaderColors.pop();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        //?}
        pop();
    }
}
