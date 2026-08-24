package me.ifair.luahud.render;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class RenderBuffer {
    private record Size(int width, int height) {
        static final Size ZERO = new Size(0, 0);
    }

    private record Point(int x, int y) {}

    private static final int LINE_SPACING = 2;
    private static final int BASE_SIZE = 16;

    private enum ElementType {
        TEXT,
        ITEM,
        TEXTURE,
        STACK,
    }

    private static class RenderElement {
        ElementType type;

        // common
        Anchor anchor;
        int baseX, baseY;
        boolean newLine;
        int backgroundColor;
        float scale;

        // text
        String text;
        int textColor;
        boolean shadow;

        // item / stack / texture
        ItemStack itemStack;
        Identifier texture;

        void clearReferences() {
            text = null;
            itemStack = null;
            texture = null;
        }
    }

    private static final List<RenderElement> ELEMENT_POOL = new ArrayList<>();
    private static int activeElements = 0; // number of active elements in the pool

    public static void clear() {
        activeElements = 0; // resetting index instead of deleting the whole pool because GC❤me
    }

    private static RenderElement obtain() {
        RenderElement element;
        if(activeElements < ELEMENT_POOL.size()) {
            element = ELEMENT_POOL.get(activeElements);
            element.clearReferences();
        } else {
            element = new RenderElement();
            ELEMENT_POOL.add(element);
        }
        activeElements++;
        return element;
    }

    public static void addText(
            String text,
            int textColor,
            int backgroundColor,
            boolean shadow,
            Anchor anchor,
            int baseX,
            int baseY,
            boolean newLine
    ) {
        RenderElement e = obtain();
        e.type = ElementType.TEXT;
        e.text = text;
        e.textColor = textColor;
        e.backgroundColor = backgroundColor;
        e.shadow = shadow;
        setCommon(e, anchor, baseX, baseY, newLine);
    }

    public static void addStack(
            ItemStack itemStack,
            float scale,
            int backgroundColor,
            Anchor anchor,
            int baseX,
            int baseY,
            boolean newLine
    ) {
        RenderElement e = obtain();
        e.type = ElementType.STACK;
        e.itemStack = itemStack;
        e.scale = scale;
        e.backgroundColor = backgroundColor;
        setCommon(e, anchor, baseX, baseY, newLine);
    }

    public static void addItem(
            ItemStack itemStack,
            float scale,
            int backgroundColor,
            Anchor anchor,
            int baseX,
            int baseY,
            boolean newLine
    ) {
        RenderElement e = obtain();
        e.type = ElementType.ITEM;
        e.itemStack = itemStack;
        e.scale = scale;
        e.backgroundColor = backgroundColor;
        setCommon(e, anchor, baseX, baseY, newLine);
    }

    public static void addTexture(
            Identifier texture,
            float scale,
            int backgroundColor,
            Anchor anchor,
            int baseX,
            int baseY,
            boolean newLine
    ) {
        RenderElement e = obtain();
        e.type = ElementType.TEXTURE;
        e.texture = texture;
        e.scale = scale;
        e.backgroundColor = backgroundColor;
        setCommon(e, anchor, baseX, baseY, newLine);
    }

    private static void setCommon(RenderElement e, Anchor anchor, int baseX, int baseY, boolean newLine) {
        e.anchor = anchor;
        e.baseX = baseX;
        e.baseY = baseY;
        e.newLine = newLine;
    }

    public static void renderAll(DrawContext context, TextRenderer textRenderer, int screenWidth, int screenHeight) {
        int currentX = 0;
        int currentY = 0;

        Anchor lastAnchor = null;
        int lastBaseX = Integer.MIN_VALUE;
        int lastBaseY = Integer.MIN_VALUE;

        for (int i = 0; i < activeElements; i++) {
            RenderElement element = ELEMENT_POOL.get(i);

            if (element.anchor != lastAnchor || element.baseX != lastBaseX || element.baseY != lastBaseY) {
                currentX = 0;
                currentY = 0;
                lastAnchor = element.anchor;
                lastBaseX = element.baseX;
                lastBaseY = element.baseY;
            }


            Size size = switch (element.type) {
                case TEXT -> renderText(context, textRenderer, element, currentX, currentY, screenWidth, screenHeight);
                case STACK, ITEM -> renderItem(context, textRenderer, element, currentX, currentY, screenWidth, screenHeight);
                case TEXTURE -> renderTexture(context, element, currentX, currentY, screenWidth, screenHeight);
            };

            if (element.newLine) {
                currentX = 0;
                currentY += size.height() + LINE_SPACING;
            } else {
                currentX += size.width();
            }
        }
    }

    private static Size renderText(
            DrawContext context,
            TextRenderer textRenderer,
            RenderElement element,
            int currentX,
            int currentY,
            int screenWidth,
            int screenHeight
    ) {
        int textWidth = textRenderer.getWidth(element.text);
        int textHeight = textRenderer.fontHeight;

        Point pos = resolvePosition(element, textWidth, textHeight, currentX, currentY, screenWidth, screenHeight);

        if (textWidth > 0) {
            drawBackground(context, element.backgroundColor, pos.x(), pos.y(), pos.x() + textWidth, pos.y() + textHeight);
        }
        context.drawText(textRenderer, element.text, pos.x(), pos.y(), element.textColor, element.shadow);

        return new Size(textWidth, textHeight);
    }

    private static Size renderItem(DrawContext context, TextRenderer textRenderer, RenderElement element, int currentX, int currentY, int screenWidth, int screenHeight) {
        if (element.itemStack == null || element.itemStack.isEmpty()) return Size.ZERO;

        float scale = element.scale > 0 ? element.scale : 1f;
        int size = Math.round(BASE_SIZE * scale);

        Point pos = resolvePosition(element, size, size, currentX, currentY, screenWidth, screenHeight);

        drawBackground(context, element.backgroundColor, pos.x(), pos.y(), pos.x() + size, pos.y() + size);

        context.getMatrices().push();
        context.getMatrices().translate(pos.x(), pos.y(), 0);
        context.getMatrices().scale(scale, scale, 1f);

        context.drawItem(element.itemStack, 0, 0);
        context.drawItemInSlot(textRenderer, element.itemStack, 0, 0);

        context.getMatrices().pop();

        return new Size(size, size);
    }

    private static Size renderTexture(DrawContext context, RenderElement element, int currentX, int currentY, int screenWidth, int screenHeight) {
        float scale = element.scale > 0 ? element.scale : 1f;
        int size = Math.round(BASE_SIZE * scale);

        Point pos = resolvePosition(element, size, size, currentX, currentY, screenWidth, screenHeight);

        drawBackground(context, element.backgroundColor, pos.x(), pos.y(), pos.x() + size, pos.y() + size);

        context.drawTexture(element.texture, pos.x(), pos.y(), 0, 0, size, size, size, size);

        return new Size(size, size);
    }

    private static void drawBackground(DrawContext context, int color, int x1, int y1, int x2, int y2) {
        if (isVisible(color)) {
            context.fill(x1, y1, x2, y2, color);
        }
    }


    private static Point resolvePosition(
            RenderElement element,
            int width,
            int height,
            int cursorX,
            int cursorY,
            int screenWidth,
            int screenHeight
    ) {
        int finalX = element.anchor.right()
                ? screenWidth - element.baseX - cursorX - width
                : element.baseX + cursorX;

        int finalY = element.anchor.bottom()
                ? screenHeight - element.baseY - cursorY - height
                : element.baseY + cursorY;

        return new Point(finalX, finalY);
    }

    private static boolean isVisible(int color) {
        return (color >> 24 & 0xFF) > 0;
    }
}