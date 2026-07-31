package me.ifair.luahud.render;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.luaj.vm2.LuaTable;

import java.util.ArrayList;
import java.util.List;

public class RenderBuffer {
    // A simple class that holds the data instead of lambda
    private static class TextElement {
        String text;
        int color, bgColor, xOffset, yOffset;
        String anchor;

        void update(String text, int color, int bgColor, String anchor, int xOffset, int yOffset) {
            this.text = text;
            this.color = color;
            this.bgColor = bgColor;
            this.anchor = anchor;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
        }
    }

    // Object Pool
    private static final List<TextElement> TEXT_POOL = new ArrayList<>();
    private static int activeElements = 0; // counter for active texts on the screen

    private static SectionState currentState = new SectionState();

    public static void clear() {
        activeElements = 0; // Reset the index rather than deleting the list, so object stays on the ram and there is fewer problems with GC
        currentState = new SectionState();
    }

    public static void setSection(LuaTable options) {
        currentState = new SectionState();

        if (options != null && !options.isnil()) {
            if (!options.get("anchor").isnil()) currentState.anchor = options.get("anchor").checkjstring();
            if (!options.get("textColor").isnil()) currentState.textColor = parseColor(options.get("textColor").checkjstring());
            if (!options.get("backgroundColor").isnil()) currentState.backgroundColor = parseColor(options.get("backgroundColor").checkjstring());
            if (!options.get("offsetX").isnil()) currentState.offsetX = options.get("offsetX").checkint();
            if (!options.get("offsetY").isnil()) currentState.offsetY = options.get("offsetY").checkint();
        }
        currentState.cursorY = 0; // Resetting the cursorY because I'm on a new section
    }

    public static void addText(String text, LuaTable options) {
        TextElement element;
        // use If there is already a ready object, else create one and add it to the pool
        if (activeElements < TEXT_POOL.size()) {
            element = TEXT_POOL.get(activeElements);
        } else {
            element = new TextElement();
            TEXT_POOL.add(element);
        }

        element.update(
                text,
                currentState.textColor,
                currentState.backgroundColor,
                currentState.anchor,
                currentState.offsetX,
                currentState.offsetY + currentState.cursorY
        );

        activeElements++;
        currentState.cursorY += 10;
    }

    public static void renderAll(DrawContext context, TextRenderer textRenderer, int screenWidth, int screenHeight) {
        // Draw only the active elements
        for (int i = 0; i < activeElements; i++) {
            TextElement el = TEXT_POOL.get(i);

            int finalX = el.xOffset;
            int finalY = el.yOffset;
            int textWidth = textRenderer.getWidth(el.text);
            int textHeight = textRenderer.fontHeight;

            switch (el.anchor) {
                case "topRight":
                    finalX = screenWidth - textWidth - el.xOffset;
                    break;
                case "bottomLeft":
                    finalY = screenHeight - textHeight - el.yOffset;
                    break;
                case "bottomRight":
                    finalX = screenWidth - textWidth - el.xOffset;
                    finalY = screenHeight - textHeight - el.yOffset;
                    break;
            }

            // Draw background if background's alpha is not 00, and text width is bigger than zero
            if (((el.bgColor >> 24 & 0xFF) > 0) && textWidth > 0) {
                context.fill(finalX - 2, finalY - 1, finalX + textWidth + 2, finalY + textHeight, el.bgColor);
            }
            context.drawText(textRenderer, el.text, finalX, finalY, el.color, true);
        }
    }

    private static int parseColor(String hex) {
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() == 6) hex = "FF" + hex;
        return (int) Long.parseLong(hex, 16);
    }
}