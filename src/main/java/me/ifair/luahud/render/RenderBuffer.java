package me.ifair.luahud.render;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

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

    public static void beginSection(SectionLayout layout) {
        QUEUE.beginSection(layout);
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
        if (cachedLayout == null
                || cachedTextRenderer != textRenderer
                || cachedScreenWidth != screenWidth
                || cachedScreenHeight != screenHeight) {
            cachedLayout = LAYOUT_ENGINE.layout(
                    QUEUE.sections(), element -> RenderElementMeasurer.width(element, textRenderer), screenWidth, screenHeight);
            cachedTextRenderer = textRenderer;
            cachedScreenWidth = screenWidth;
            cachedScreenHeight = screenHeight;
        }
        RenderPainter.paint(context, textRenderer, cachedLayout);
    }

    private static void invalidateLayout() {
        cachedLayout = null;
        cachedTextRenderer = null;
    }

}
