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
        int color, bgColor, baseX, baseY;
        String anchor;
        boolean shadow;
        boolean isNewLine;

        void update(String text, int color, int bgColor, boolean shadow, String anchor, int baseX, int baseY, boolean isNewLine) {
            this.text = text;
            this.color = color;
            this.bgColor = bgColor;
            this.shadow = shadow;
            this.anchor = anchor;
            this.baseX = baseX;
            this.baseY = baseY;
            this.isNewLine = isNewLine;
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
            if (!options.get("shadow").isnil()) currentState.shadow = options.get("shadow").checkboolean();
        }
    }

    // newLine = true  -> drawln() behaviour (starts a fresh line)
    // newLine = false -> draw() behaviour (continues on the same line)
    public static void addText(String text, LuaTable options, boolean newLine) {
        TextElement element;
        // use If there is already a ready object, else create one and add it to the pool
        if (activeElements < TEXT_POOL.size()) {
            element = TEXT_POOL.get(activeElements);
        } else {
            element = new TextElement();
            TEXT_POOL.add(element);
        }

        int finalColor = currentState.textColor;
        int finalBgColor = currentState.backgroundColor;
        boolean finalShadow = currentState.shadow;

        if (options != null && !options.isnil()) {
            if (!options.get("textColor").isnil()) finalColor = parseColor(options.get("textColor").checkjstring());
            if (!options.get("backgroundColor").isnil()) finalBgColor = parseColor(options.get("backgroundColor").checkjstring());
            if (!options.get("shadow").isnil()) finalShadow = options.get("shadow").checkboolean();
        }

        element.update(
                text,
                finalColor,
                finalBgColor,
                finalShadow,
                currentState.anchor,
                currentState.offsetX,
                currentState.offsetY,
                newLine
        );

        activeElements++;
    }

    public static void renderAll(DrawContext context, TextRenderer textRenderer, int screenWidth, int screenHeight) {
        int currentX = 0;
        int currentY = 0;

        // TODO: Find a better way to actually understand a new section starts
        String lastAnchor = "";
        int lastBaseX = -1;
        int lastBaseY = -1;

        // Draw only the active elements
        for (int i = 0; i < activeElements; i++) {
            TextElement element = TEXT_POOL.get(i);

            if (!element.anchor.equals(lastAnchor) || element.baseX != lastBaseX || element.baseY != lastBaseY) {
                currentX = 0;
                currentY = 0;
                lastAnchor = element.anchor;
                lastBaseX = element.baseX;
                lastBaseY = element.baseY;
            }

            int finalX = element.baseX + currentX;
            int finalY = element.baseY + currentY;
            int textWidth = textRenderer.getWidth(element.text);
            int textHeight = textRenderer.fontHeight;

            switch (element.anchor) {
                case "topRight":
                    finalX = screenWidth - textWidth - element.baseX - currentX;
                    break;
                case "bottomLeft":
                    finalY = screenHeight - textHeight - element.baseY - currentY;
                    break;
                case "bottomRight":
                    finalX = screenWidth - textWidth - element.baseX - currentX;
                    finalY = screenHeight - textHeight - element.baseY - currentY;
                    break;
            }

            // Draw background if background's alpha is not 00, and text width is bigger than zero
            if (((element.bgColor >> 24 & 0xFF) > 0) && textWidth > 0) {
                context.fill(finalX - 2, finalY - 1, finalX + textWidth + 2, finalY + textHeight, element.bgColor);
            }
            context.drawText(textRenderer, element.text, finalX, finalY, element.color, element.shadow);

            // New Line Logic
            if (element.isNewLine) {
                currentX = 0;

                if (element.anchor.equals("bottomLeft") || element.anchor.equals("bottomRight"))
                    currentY -= textHeight + 2; // Up ↑
                else
                    currentY += textHeight + 2; // Down ↓
            } else {
                if (element.anchor.equals("topLeft") || element.anchor.equals("bottomLeft"))
                    currentX += textWidth + 4; // Right →
                else
                    currentX -= textWidth + 4; // Left ←
            }
        }
    }

    private static int parseColor(String hex) {
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() == 6) hex = "FF" + hex;
        return (int) Long.parseLong(hex, 16);
    }
}