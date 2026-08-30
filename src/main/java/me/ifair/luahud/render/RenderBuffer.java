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

    private static final int ROW_HEIGHT = 12;
    private static final int BASE_SIZE = 12;

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
        if (activeElements < ELEMENT_POOL.size()) {
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
        int currentYOffset = 0;

        Anchor lastAnchor = null;
        int lastBaseX = Integer.MIN_VALUE;
        int lastBaseY = Integer.MIN_VALUE;

        int i = 0;
        while (i < activeElements) {
            RenderElement firstInRow = ELEMENT_POOL.get(i);

            if (firstInRow.anchor != lastAnchor || firstInRow.baseX != lastBaseX || firstInRow.baseY != lastBaseY) {
                currentYOffset = 0;
                lastAnchor = firstInRow.anchor;
                lastBaseX = firstInRow.baseX;
                lastBaseY = firstInRow.baseY;
            }

            int rowWidth = 0;
            int j = i;
            while (j < activeElements) {
                RenderElement e = ELEMENT_POOL.get(j);

                // If somehow stupid user decides to change the section mid-row, break the row calculation
                if (e.anchor != lastAnchor || e.baseX != lastBaseX || e.baseY != lastBaseY) {
                    j--;
                    break;
                }

                rowWidth += measureWidth(e, textRenderer);

                if (e.newLine) {
                    break; // End of row
                }
                j++;
            }

            int endIdx = Math.min(j, activeElements - 1);


            // Calc the absolute start position for the entire row
            int startX = lastAnchor.right()
                    ? screenWidth - lastBaseX - rowWidth
                    : lastBaseX;

            int startY = lastAnchor.bottom()
                    ? screenHeight - lastBaseY - currentYOffset - ROW_HEIGHT
                    : lastBaseY + currentYOffset;

            int currentDrawX = startX;

            for (int k = i; k <= endIdx; k++) {
                RenderElement e = ELEMENT_POOL.get(k);

                int elementWidth = measureWidth(e, textRenderer);

                drawBackground(context, e.backgroundColor, currentDrawX, startY, currentDrawX + elementWidth, startY + ROW_HEIGHT);

                renderContentAt(context, textRenderer, e, currentDrawX, startY);

                currentDrawX += elementWidth;
            }

            currentYOffset += ROW_HEIGHT;
            i = endIdx + 1;
        }
    }

    private static int measureWidth(RenderElement element, TextRenderer textRenderer) {
        return switch (element.type) {
            case TEXT -> textRenderer.getWidth(element.text);
            case STACK, ITEM -> {
                if (element.itemStack == null || element.itemStack.isEmpty()) yield 0;
                float scale = element.scale > 0 ? element.scale : 1f;
                yield Math.round(BASE_SIZE * scale);
            }
            case TEXTURE -> {
                float scale = element.scale > 0 ? element.scale : 1f;
                yield Math.round(BASE_SIZE * scale);
            }
        };
    }

    private static void renderContentAt(DrawContext context, TextRenderer textRenderer, RenderElement element, int x, int y) {
        switch (element.type) {
            case TEXT -> {
                int textY = y + (ROW_HEIGHT - textRenderer.fontHeight) / 2;
                context.drawText(textRenderer, element.text, x, textY, element.textColor, element.shadow);
            }
            case STACK, ITEM -> {
                if (element.itemStack == null || element.itemStack.isEmpty()) return;

                float scale = element.scale > 0 ? element.scale : 1f;
                int size = Math.round(BASE_SIZE * scale);
                float matrixScale = (BASE_SIZE / 16f) * scale;

                int itemY = y + (ROW_HEIGHT - size) / 2 - 1;

                context.getMatrices().push();
                context.getMatrices().translate(x, itemY, 0);
                context.getMatrices().scale(matrixScale, matrixScale, 1f);

                context.drawItem(element.itemStack, 0, 0);
                context.drawItemInSlot(textRenderer, element.itemStack, 0, 0);

                context.getMatrices().pop();
            }
            case TEXTURE -> {
                float scale = element.scale > 0 ? element.scale : 1f;
                int size = Math.round(BASE_SIZE * scale);
                int textureY = y + (ROW_HEIGHT - size) / 2 - 1;

                context.drawTexture(element.texture, x, textureY, 0, 0, size, size, size, size);
            }
        }
    }

    private static void drawBackground(DrawContext context, int color, int x1, int y1, int x2, int y2) {
        if (isVisible(color)) {
            context.fill(x1, y1 - 1, x2, y2 - 1, color);
        }
    }

    private static boolean isVisible(int color) {
        return (color >> 24 & 0xFF) > 0;
    }
}