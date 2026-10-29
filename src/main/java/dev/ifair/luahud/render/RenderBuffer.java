package dev.ifair.luahud.render;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.List;

public final class RenderBuffer {
    private static final RenderQueue QUEUE = new RenderQueue();
    private static final RenderLayoutEngine LAYOUT_ENGINE = new RenderLayoutEngine();
    private static RenderLayout cachedLayout;
    private static TextRenderer cachedTextRenderer;
    private static int cachedScreenWidth;
    private static int cachedScreenHeight;

    private RenderBuffer() {}

    public static void clear() {
        QUEUE.clear();
        invalidateLayout();
    }

    public static void configureClock(boolean gameTime) {
        QUEUE.configureClock(gameTime);
        invalidateLayout();
    }

    public static void tickClock() { QUEUE.tickClock(); }

    public static void beginInvocation(Object owner) {
        QUEUE.beginInvocation(owner);
        invalidateLayout();
    }

    public static void endInvocation() { QUEUE.endInvocation(); }

    public static void selectDefault(SectionLayout layout) { QUEUE.selectDefault(layout); }

    public static void beginSection(SectionLayout layout, String id, Double duration) {
        QUEUE.beginSection(layout, id, duration);
        invalidateLayout();
    }

    public static void hideSection(String id) {
        QUEUE.hideSection(id);
        invalidateLayout();
    }

    public static void beginSection(SectionLayout layout) {
        QUEUE.beginSection(layout);
        invalidateLayout();
    }

    public static boolean isCanvas() { return QUEUE.isCanvas(); }

    public static void beginCanvas(SectionLayout layout, float width, float height, float scale,
                                   boolean clip, boolean visible, boolean inline, String id, Double duration) {
        QUEUE.beginCanvas(layout, width, height, scale, clip, visible, inline, id, duration);
        invalidateLayout();
    }

    public static void endCanvas() {
        if (!QUEUE.isCanvas()) throw new IllegalStateException("No canvas is selected");
        QUEUE.closeCanvas();
    }

    public static void canvasTransform(CanvasTransform transform) { QUEUE.transform(transform); }

    public static void addMesh(List<CanvasGeometry.Quad> quads) {
        QUEUE.command(new CanvasDrawing.Mesh(quads));
        invalidateLayout();
    }

    public static void addImage(Identifier texture, int sourceWidth, int sourceHeight, float width, float height,
                                int sourceX, int sourceY, int regionWidth, int regionHeight,
                                int left, int right, int top, int bottom, boolean nineSlice,
                                CanvasTransform transform) {
        QUEUE.command(new CanvasDrawing.Image(texture, sourceWidth, sourceHeight, width, height,
                sourceX, sourceY, regionWidth, regionHeight, left, right, top, bottom, nineSlice, transform));
        invalidateLayout();
    }

    public static void newLine(int count) {
        if (count <= 0) throw new IllegalArgumentException("Newline count must be greater than zero");
        for (int i = 0; i < count; i++) QUEUE.newLine();
        invalidateLayout();
    }

    public static void addText(
            String text,
            int textColor,
            int backgroundColor,
            boolean shadow,
            int offsetX,
            int offsetY,
            boolean newLine
    ) {
        QUEUE.add(new RenderElement.Text(
                text, textColor, backgroundColor, shadow, offsetX, offsetY), newLine);
        invalidateLayout();
    }

    public static void addStack(
            ItemStack itemStack,
            float scale,
            int backgroundColor,
            int offsetX,
            int offsetY,
            boolean newLine,
            boolean drawAir
    ) {
        QUEUE.add(new RenderElement.Stack(
                itemStack, scale, backgroundColor, offsetX, offsetY, drawAir), newLine);
        invalidateLayout();
    }

    public static void addItem(
            ItemStack itemStack,
            float scale,
            int backgroundColor,
            int offsetX,
            int offsetY,
            boolean newLine
    ) {
        QUEUE.add(new RenderElement.Item(
                itemStack, scale, backgroundColor, offsetX, offsetY), newLine);
        invalidateLayout();
    }

    public static void addEntity(
            Entity entity,
            float scale,
            float yaw,
            float pitch,
            boolean fullBright,
            boolean useEntityRotation,
            int backgroundColor,
            int offsetX,
            int offsetY,
            boolean newLine
    ) {
        QUEUE.add(new RenderElement.Entity(
                entity, scale, yaw, pitch, fullBright, useEntityRotation,
                backgroundColor, offsetX, offsetY), newLine);
        invalidateLayout();
    }

    public static void addTexture(
            Identifier texture,
            float scale,
            int backgroundColor,
            int offsetX,
            int offsetY,
            boolean newLine
    ) {
        QUEUE.add(new RenderElement.Texture(
                texture, scale, backgroundColor, offsetX, offsetY), newLine);
        invalidateLayout();
    }

    public static void renderAll(DrawContext context, TextRenderer textRenderer, int screenWidth, int screenHeight) {
        if (QUEUE.expire()) invalidateLayout();
        if (cachedLayout == null
                || cachedTextRenderer != textRenderer
                || cachedScreenWidth != screenWidth
                || cachedScreenHeight != screenHeight) {
            cachedLayout = LAYOUT_ENGINE.layout(
                    QUEUE.sections(),
                    (element, sectionScale) -> RenderElementMeasurer.width(element, textRenderer, sectionScale),
                    (element, sectionScale) -> RenderElementMeasurer.height(element, textRenderer, sectionScale),
                    screenWidth,
                    screenHeight);
            cachedTextRenderer = textRenderer;
            cachedScreenWidth = screenWidth;
            cachedScreenHeight = screenHeight;
        }
        RenderPainter.paint(context, textRenderer, cachedLayout);
    }

    static List<RenderSection> sections() { return QUEUE.sections(); }

    private static void invalidateLayout() {
        cachedLayout = null;
        cachedTextRenderer = null;
    }

}
