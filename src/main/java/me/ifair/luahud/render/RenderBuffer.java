package me.ifair.luahud.render;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.luaj.vm2.LuaTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class RenderBuffer {
    private static final List<RenderTask> TASKS = new ArrayList<>();
    private static final Stack<SectionState> STATE_STACK = new Stack<>();
    private static SectionState currentState = new SectionState();

    public static void clear() {
        TASKS.clear();
        STATE_STACK.clear();
        currentState = new SectionState();
    }

    public static void pushSection(LuaTable options) {
        STATE_STACK.push(currentState.copy());

        if (options != null && !options.isnil()) {
            if (!options.get("anchor").isnil()) currentState.anchor = options.get("anchor").checkjstring();
            if (!options.get("textColor").isnil()) currentState.textColor = parseColor(options.get("textColor").checkjstring());
            if (!options.get("backgroundColor").isnil()) currentState.backgroundColor = parseColor(options.get("backgroundColor").checkjstring());
            if (!options.get("offsetX").isnil()) currentState.offsetX += options.get("offsetX").checkint();
            if (!options.get("offsetY").isnil()) currentState.offsetY += options.get("offsetY").checkint();
        }

        currentState.cursorY = 0;
    }

    public static void popSection() {
        if (!STATE_STACK.isEmpty()) {
            currentState = STATE_STACK.pop();
        }
    }

    public static void addText(String text, LuaTable options) {
        final int color = currentState.textColor;
        final int bgColor = currentState.backgroundColor;
        final String anchor = currentState.anchor;
        final int xOffset = currentState.offsetX;
        final int yOffset = currentState.offsetY + currentState.cursorY;

        TASKS.add(((context, textRenderer, screenWidth, screenHeight) -> {
            int finalX = xOffset;
            int finalY = yOffset;

            int textWidth = textRenderer.getWidth(text);
            int textHeight = textRenderer.fontHeight;

            switch (anchor) {
                case "topRight":
                    finalX = screenWidth - textWidth - xOffset;
                    break;
                case "bottomLeft":
                    finalY = screenHeight - textHeight - yOffset;
                    break;
                case "bottomRight":
                    finalX = screenWidth - textWidth - xOffset;
                    finalY = screenHeight - textHeight - yOffset;
                    break;
            }

            // I copied these bit operations, but the thing I know is that it checks whether there is a backgroundColor or not
            if ((bgColor >> 24 & 0xFF) > 0) {
                context.fill(finalX - 2, finalY - 2, finalX + textWidth + 2, finalY + textHeight + 2, bgColor);
            }

            context.drawText(textRenderer, text, finalX, finalY, color, true);
        }));

        currentState.cursorY += 10; // Go down 10y for the next line
    }

    public static void renderAll(DrawContext context, TextRenderer textRenderer, int screenWidth, int screenHeight) {
        for (RenderTask task : TASKS) {
            task.draw(context, textRenderer, screenWidth, screenHeight);
        }
    }

    private static int parseColor(String hex) {
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() == 6) hex = "FF" + hex;
        return (int) Long.parseLong(hex, 16);
    }
}