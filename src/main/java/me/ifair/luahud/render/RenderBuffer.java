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
}