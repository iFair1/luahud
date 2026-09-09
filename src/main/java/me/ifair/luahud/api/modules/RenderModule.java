package me.ifair.luahud.api.modules;

import me.ifair.luahud.LuaHUD;
import me.ifair.luahud.api.*;
import me.ifair.luahud.render.Anchor;
import me.ifair.luahud.render.RenderBuffer;
import me.ifair.luahud.render.SectionState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class RenderModule {
    private static SectionState currentState = new SectionState();

    private RenderModule() {}

    public static void resetSection() {
        currentState = new SectionState();
    }

    public static void register(LuaApiRegistrar api) {
        LuaType<Object> color = LuaType.union(LuaType.string(), LuaType.integer());
        LuaType<LuaTableValue> sectionOptions = optionsType("SectionOptions", color, true, true, false);
        LuaType<LuaTableValue> textOptions = optionsType("TextOptions", color, false, true, false);
        LuaType<LuaTableValue> iconOptions = optionsType("IconOptions", color, false, false, false);
        LuaType<LuaTableValue> stackOptions = optionsType("StackOptions", color, false, false, true);

        FunctionBuilder<Void> section = api.root().function("section", LuaType.nil(), "Reset drawing state and apply section options.");
        LuaParameter<LuaTableValue> sectionArg = section.optionalParameter("options", sectionOptions, "Section options");
        section.call(arguments -> {
            applySection(arguments.get(sectionArg));
            return null;
        });

        registerDraw(api, "draw", false, textOptions);
        registerDraw(api, "drawln", true, textOptions);

        FunctionBuilder<Void> drawStack = api.root().function("drawStack", LuaType.nil(), "Draw an item stack.");
        LuaParameter<LuaTableValue> stackArg = drawStack.parameter("stack", itemStackInput(), "Stack data");
        LuaParameter<LuaTableValue> stackOpts = drawStack.optionalParameter("options", stackOptions, "Rendering options");
        drawStack.call(arguments -> {
            drawStack(arguments.get(stackArg), arguments.get(stackOpts));
            return null;
        });

        FunctionBuilder<Void> drawItem = api.root().function("drawItem", LuaType.nil(), "Draw an item by namespaced ID.");
        LuaParameter<String> itemId = drawItem.parameter("id", LuaType.string(), "Namespaced item ID");
        LuaParameter<LuaTableValue> itemOpts = drawItem.optionalParameter("options", iconOptions, "Rendering options");
        drawItem.call(arguments -> {
            drawItem(arguments.get(itemId), arguments.get(itemOpts));
            return null;
        });

        FunctionBuilder<Void> drawTexture = api.root().function("drawTexture", LuaType.nil(), "Draw a texture by namespaced path.");
        LuaParameter<String> texturePath = drawTexture.parameter("path", LuaType.string(), "Namespaced texture path");
        LuaParameter<LuaTableValue> textureOpts = drawTexture.optionalParameter("options", iconOptions, "Rendering options");
        drawTexture.call(arguments -> {
            drawTexture(arguments.get(texturePath), arguments.get(textureOpts));
            return null;
        });
    }

    private static void registerDraw(LuaApiRegistrar api, String name, boolean newline, LuaType<LuaTableValue> type) {
        FunctionBuilder<Void> draw = api.root().function(name, LuaType.nil(),
                newline ? "Draw text and advance to the next line." : "Draw text.");
        LuaParameter<Object> text = draw.parameter("text", LuaType.any(), "Value converted to text");
        LuaParameter<LuaTableValue> options = draw.optionalParameter("options", type, "Text options");
        draw.call(arguments -> {
            drawText(String.valueOf(arguments.get(text)), arguments.get(options), newline);
            return null;
        });
    }

    private static LuaType<LuaTableValue> optionsType(String name, LuaType<Object> color, boolean section, boolean text, boolean stack) {
        return LuaType.inputTable(name, fields -> {
            fields.optional("backgroundColor", color, "ARGB integer or RGB/ARGB hex string")
                    .optional("offsetX", LuaType.integer(), "Horizontal offset")
                    .optional("offsetY", LuaType.integer(), "Vertical offset");
            if (section) {
                fields.optional("anchor", LuaType.union(
                        LuaType.literal("topLeft"), LuaType.literal("bottomLeft"),
                        LuaType.literal("topRight"), LuaType.literal("bottomRight")), "Screen anchor");
            }
            if (section || text) {
                fields.optional("textColor", color, "Text color")
                        .optional("shadow", LuaType.bool(), "Draw text shadow");
            }
            if (section || !text) fields.optional("scale", LuaType.number(), "Drawing scale");
            if (stack) {
                fields.optional("showDurabilityBar", LuaType.bool(), "Show durability bar")
                        .optional("showCount", LuaType.bool(), "Show stack count")
                        .optional("showGlint", LuaType.bool(), "Show enchantment glint");
            }
        });
    }

    private static LuaType<LuaTableValue> itemStackInput() {
        return LuaType.inputTable("ItemStackInput", fields -> fields
                .field("id", LuaType.string(), "Namespaced item ID")
                .optional("count", LuaType.integer(), "Stack count")
                .optional("damage", LuaType.integer(), "Item damage")
                .optional("enchantments", LuaType.map(LuaType.integer()), "Enchantments"));
    }

    private static void applySection(LuaTableValue options) {
        currentState = new SectionState();
        if (options == null) return;
        String anchor = options.string("anchor", null);
        if (anchor != null) currentState.anchor = Anchor.fromString(anchor);
        if (options.contains("textColor")) currentState.textColor = color(options.get("textColor"));
        if (options.contains("backgroundColor")) currentState.backgroundColor = color(options.get("backgroundColor"));
        currentState.offsetX = options.integer("offsetX", currentState.offsetX);
        currentState.offsetY = options.integer("offsetY", currentState.offsetY);
        currentState.shadow = options.bool("shadow", currentState.shadow);
        currentState.scale = (float) options.number("scale", currentState.scale);
    }

    private static void drawText(String text, LuaTableValue options, boolean newline) {
        RenderBuffer.addText(text,
                resolveColor(options, "textColor", currentState.textColor),
                resolveColor(options, "backgroundColor", currentState.backgroundColor),
                options == null ? currentState.shadow : options.bool("shadow", currentState.shadow),
                currentState.anchor, currentState.offsetX, currentState.offsetY, newline);
    }

    private static void drawStack(LuaTableValue stack, LuaTableValue options) {
        boolean showCount = options == null || options.bool("showCount", true);
        boolean showDurability = options == null || options.bool("showDurabilityBar", true);
        boolean showGlint = options == null
                ? stack.contains("enchantments") : options.bool("showGlint", stack.contains("enchantments"));
        Identifier id = Identifier.tryParse(stack.string("id", ""));
        Item item = id == null ? Items.AIR : Registries.ITEM.get(id);
        ItemStack value = new ItemStack(item, showCount ? stack.integer("count", 1) : 1);
        if (showDurability && stack.contains("damage")) value.setDamage(stack.integer("damage", 0));
        if (showGlint) value.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        RenderBuffer.addStack(value, scale(options), resolveColor(options, "backgroundColor", currentState.backgroundColor),
                currentState.anchor, currentState.offsetX, currentState.offsetY, false);
    }

    private static void drawItem(String id, LuaTableValue options) {
        Identifier identifier = Identifier.tryParse(id);
        Item item = identifier == null ? Items.AIR : Registries.ITEM.get(identifier);
        RenderBuffer.addItem(new ItemStack(item), scale(options), resolveColor(options, "backgroundColor", currentState.backgroundColor),
                currentState.anchor, currentState.offsetX, currentState.offsetY, false);
    }

    private static void drawTexture(String path, LuaTableValue options) {
        Identifier texture = Identifier.tryParse(path);
        if (texture == null) {
            LuaHUD.LOGGER.error("Invalid texture path! Skipping. ({})", path);
            return;
        }
        RenderBuffer.addTexture(texture, scale(options), resolveColor(options, "backgroundColor", currentState.backgroundColor),
                currentState.anchor, currentState.offsetX, currentState.offsetY, false);
    }

    private static float scale(LuaTableValue options) {
        return options == null ? currentState.scale : (float) options.number("scale", currentState.scale);
    }

    private static int resolveColor(LuaTableValue options, String key, int fallback) {
        return options != null && options.contains(key) ? color(options.get(key)) : fallback;
    }

    private static int color(Object value) {
        if (value instanceof Number number) {
            long color = number.longValue();
            if ((color & 0xFF000000L) == 0 && color <= 0xFFFFFF) color |= 0xFF000000L;
            return (int) color;
        }
        String hex = String.valueOf(value);
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() == 6) hex = "FF" + hex;
        return (int) Long.parseLong(hex, 16);
    }
}
