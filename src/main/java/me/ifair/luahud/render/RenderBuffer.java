package me.ifair.luahud.render;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.luaj.vm2.LuaTable;

import java.util.ArrayList;
import java.util.List;

public class RenderBuffer {
    public enum ElementType {
        TEXT,
        ITEM,
        TEXTURE,
        STACK,
    }

    private static class RenderElement {
        ElementType type;

        // common
        String anchor;
        int baseX, baseY;
        boolean newLine;
        int backgroundColor;
        float scale;

        // text
        String text;
        int textColor;
        boolean shadow;

        // item / stack
        // This is not exactly like this but not a problem for now
        ItemStack itemStack;
        boolean showDurabilityBar;
        boolean showCount;
        boolean showGlint;

        // Texture
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
            String anchor,
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
        e.anchor = anchor;
        e.baseX = baseX;
        e.baseY = baseY;
        e.newLine = newLine;
    }

    public static void addStack(
            ItemStack itemStack,
            float scale,
            boolean showDurabilityBar,
            boolean showCount,
            boolean showGlint,
            int backgroundColor,
            String anchor,
            int baseX,
            int baseY,
            boolean newLine
    ) {
        RenderElement e = obtain();
        e.type = ElementType.STACK;
        e.itemStack = itemStack;
        e.scale = scale;
        e.showDurabilityBar = showDurabilityBar;
        e.showCount = showCount;
        e.showGlint = showGlint;
        e.backgroundColor = backgroundColor;
        e.anchor = anchor;
        e.baseX = baseX;
        e.baseY = baseY;
        e.newLine = newLine;
    }

    public static void addItem(
            ItemStack itemStack,
            float scale,
            int backgroundColor,
            String anchor,
            int baseX,
            int baseY,
            boolean newLine
    ) {
        RenderElement e = obtain();
        e.type = ElementType.ITEM;
        e.itemStack = itemStack;
        e.scale = scale;
        e.backgroundColor = backgroundColor;
        e.anchor = anchor;
        e.baseX = baseX;
        e.baseY = baseY;
        e.newLine = newLine;
    }

    public static void addTexture(
            Identifier texture,
            float scale,
            int backgroundColor,
            String anchor,
            int baseX,
            int baseY,
            boolean newLine
    ) {
        RenderElement e = obtain();
        e.type = ElementType.TEXTURE;
        e.texture = texture;
        e.scale = scale;
        e.backgroundColor = backgroundColor;
        e.anchor = anchor;
        e.baseX = baseX;
        e.baseY = baseY;
        e.newLine = newLine;
    }

    public static void renderAll(DrawContext context, TextRenderer textRenderer, int screenWidth, int screenHeight) {
        int currentX = 0;
        int currentY = 0;

        String lastAnchor = "";
        int lastBaseX = -1;
        int lastBaseY = -1;

        for (int i = 0; i < activeElements; i++) {
            RenderElement element = ELEMENT_POOL.get(i);

            if (!element.anchor.equals(lastAnchor) || element.baseX != lastBaseX || element.baseY != lastBaseY) {
                currentX = 0;
                currentY = 0;
                lastAnchor = element.anchor;
                lastBaseX = element.baseX;
                lastBaseY = element.baseY;
            }

            int width;
            int height;

            switch (element.type) {
                case TEXT:
                    int[] textSize = renderText(context, textRenderer, element, currentX, currentY, screenWidth, screenHeight);
                    width = textSize[0];
                    height = textSize[1];
                    break;
                case STACK:
                case ITEM:
                    int[] itemSize = renderItem(context, textRenderer, element, currentX, currentY, screenWidth, screenHeight);
                    width = itemSize[0];
                    height = itemSize[1];
                    break;
                case TEXTURE:
                    int[] textSize = renderTexture(context, element, currentX, currentY, screenWidth, screenHeight);
                    width = textSize[0];
                    height = textSize[1];
                    break;
                default:
                    width = 0;
                    height = 0;
            }

            if (element.newLine) {
                currentX = 0;

                if (element.anchor.equals("bottomLeft") || element.anchor.equals("bottomRight"))
                    currentY -= height + 2; // Up ↑
                else
                    currentY += height + 2; // Down ↓
            } else {
                if (element.anchor.equals("topLeft") || element.anchor.equals("bottomLeft"))
                    currentX += width; // Right →
                else
                    currentX -= width; // Left ←
            }
        }
    }

    private static int[] renderText(
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

        int[] pos = resolvePosition(element, textWidth, textHeight, currentX, currentY, screenWidth, screenHeight);
        int finalX = pos[0];
        int finalY = pos[1];

        if (isVisible(element.backgroundColor) && textWidth > 0) {
            context.fill(finalX - 2, finalY - 1, finalX + textWidth + 2, finalY + textHeight, element.backgroundColor);
        }
        context.drawText(textRenderer, element.text, finalX, finalY, element.textColor, element.shadow);

        return new int[]{textWidth, textHeight};
    }

    private static int[] renderItem(DrawContext context, TextRenderer textRenderer, RenderElement element, int currentX, int currentY, int screenWidth, int screenHeight) {
        if (element.itemStack == null || element.itemStack.isEmpty()) return new int[]{0, 0};

        int size = 16; // TODO: Make this scalable

        int pos[] = resolvePosition(element, size, size, currentX, currentY, screenWidth, screenHeight);
        int finalX = pos[0];
        int finalY = pos[1];

        if (isVisible(element.backgroundColor)) {
            context.fill(finalX - 1, finalY - 1, finalX + size + 1, finalY + size + 1, element.backgroundColor);
        }

        context.drawItem(element.itemStack, finalX, finalY);
        context.drawItemInSlot(textRenderer, element.itemStack, finalX, finalY); // FIXME: Right now, this doesn't care about "showDurabilityBar" or "showCount". These should be handled when constructing the ItemStack

        return new int[]{size, size};
    }

    private static int[] renderTexture(DrawContext context, RenderElement element, int currentX, int currentY, int screenWidth, int screenHeight) {
        int size = 16; // TODO: Make this scalable

        int[] pos = resolvePosition(element, size, size, currentX, currentY, screenWidth, screenHeight);
        int finalX = pos[0];
        int finalY = pos[1];

        if (isVisible(element.backgroundColor)) {
            context.fill(finalX - 1, finalY - 1, finalX + size + 1, finalY + size + 1, element.backgroundColor);
        }

        context.drawTexture(element.texture, finalX, finalY, 0, 0, size, size, size, size);

        return new int[]{size, size};
    }

    private static int[] resolvePosition(RenderElement element, int width, int height, int currentX, int currentY, int screenWidth, int screenHeight) {
        // "topLeft"
        int finalX = element.baseX + currentX;
        int finalY = element.baseY + currentY;

        switch (element.anchor) {
            case "topRight":
                finalX = screenWidth - width - element.baseX - currentX;
                break;
            case "bottomLeft":
                finalY = screenHeight - height - element.baseY - currentY;
                break;
            case "bottomRight":
                finalX = screenWidth - width - element.baseX - currentX;
                finalY = screenHeight - height - element.baseY - currentY;
                break;
        }

        return new int[]{finalX, finalY};
    }

    private static boolean isVisible(int color) {
        return (color >> 24 & 0xFF) > 0;
    }
}