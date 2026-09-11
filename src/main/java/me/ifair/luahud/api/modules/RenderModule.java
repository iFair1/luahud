package me.ifair.luahud.api.modules;

import me.ifair.luahud.api.*;
import me.ifair.luahud.api.models.CoreModels;
import me.ifair.luahud.render.Anchor;
import me.ifair.luahud.render.InlineDirection;
import me.ifair.luahud.render.RenderBuffer;
import me.ifair.luahud.render.RowAlignment;
import me.ifair.luahud.render.RowDirection;
import me.ifair.luahud.render.SectionState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class RenderModule {
    private static SectionState currentState = SectionState.defaults();

    private RenderModule() {}

    public static void resetSection() {
        setSection(SectionState.defaults());
    }

    public static void register(LuaApiRegistrar api, CoreModels models) {
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
        LuaParameter<ItemStack> stackArg = drawStack.parameter("stack", models.itemStack().type(), "Item stack");
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
                    .optional("offsetX", LuaType.integer(), section ? "Horizontal screen-edge offset" : "Local horizontal offset")
                    .optional("offsetY", LuaType.integer(), section ? "Vertical screen-edge offset" : "Local vertical offset");
            if (section) {
                fields.optional("anchor", LuaType.union(
                                LuaType.literal("topLeft"), LuaType.literal("topCenter"), LuaType.literal("topRight"),
                                LuaType.literal("bottomLeft"), LuaType.literal("bottomCenter"), LuaType.literal("bottomRight")),
                                "Screen anchor")
                        .optional("rowAlign", LuaType.union(
                                LuaType.literal("left"), LuaType.literal("center"), LuaType.literal("right")),
                                "Alignment of rows within the widest row")
                        .optional("inlineDirection", LuaType.union(
                                LuaType.literal("leftToRight"), LuaType.literal("rightToLeft")),
                                "Visual order of elements within a row")
                        .optional("rowDirection", LuaType.union(
                                LuaType.literal("topToBottom"), LuaType.literal("bottomToTop")),
                                "Visual order of rows within the section");
            }
            if (section || text) {
                fields.optional("textColor", color, "Text color")
                        .optional("shadow", LuaType.bool(), "Draw text shadow");
            }
            if (section || !text) {
                fields.optional("scale", LuaType.number(),
                        section ? "Default item, stack, and texture scale" : "Drawing scale");
            }
            if (stack) {
                fields.optional("showDurabilityBar", LuaType.bool(), "Show durability bar")
                        .optional("showCount", LuaType.bool(), "Show stack count")
                        .optional("showGlint", LuaType.bool(), "Show enchantment glint")
                        .optional("drawAir", LuaType.bool(), "Reserve one item width for an empty stack");
            }
        });
    }

    private static void applySection(LuaTableValue options) {
        setSection(sectionState(options));
    }

    static SectionState sectionState(LuaTableValue options) {
        SectionState defaults = SectionState.defaults();
        Anchor anchor = options == null ? defaults.anchor() : Anchor.fromString(options.string("anchor", null));
        int offsetX = options == null ? anchor.defaultOffsetX() : options.integer("offsetX", anchor.defaultOffsetX());
        int offsetY = options == null ? defaults.offsetY() : options.integer("offsetY", defaults.offsetY());
        RowAlignment rowAlignment = options == null || !options.contains("rowAlign")
                ? anchor.defaultRowAlignment()
                : RowAlignment.fromString(options.string("rowAlign", null));
        InlineDirection inlineDirection = options == null || !options.contains("inlineDirection")
                ? defaults.inlineDirection()
                : InlineDirection.fromString(options.string("inlineDirection", null));
        RowDirection rowDirection = options == null || !options.contains("rowDirection")
                ? defaults.rowDirection()
                : RowDirection.fromString(options.string("rowDirection", null));

        return new SectionState(
                anchor,
                offsetX,
                offsetY,
                rowAlignment,
                inlineDirection,
                rowDirection,
                options == null ? defaults.shadow() : options.bool("shadow", defaults.shadow()),
                options != null && options.contains("textColor") ? color(options.get("textColor")) : defaults.textColor(),
                options != null && options.contains("backgroundColor")
                        ? color(options.get("backgroundColor")) : defaults.backgroundColor(),
                scale(options, defaults.scale())
        );
    }

    private static void setSection(SectionState state) {
        currentState = state;
        RenderBuffer.beginSection(state.layout());
    }

    private static void drawText(String text, LuaTableValue options, boolean newline) {
        RenderBuffer.addText(text,
                resolveColor(options, "textColor", currentState.textColor()),
                resolveColor(options, "backgroundColor", currentState.backgroundColor()),
                options == null ? currentState.shadow() : options.bool("shadow", currentState.shadow()),
                offset(options, "offsetX"), offset(options, "offsetY"), newline);
    }

    private static void drawStack(ItemStack stack, LuaTableValue options) {
        boolean showCount = options == null || options.bool("showCount", true);
        boolean showDurability = options == null || options.bool("showDurabilityBar", true);
        boolean drawAir = options != null && options.bool("drawAir", false);
        ItemStack value = stack.copy();
        if (!showCount) value.setCount(1);
        if (!showDurability) value.setDamage(0);
        if (options != null && options.contains("showGlint")) {
            value.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, options.bool("showGlint", true));
        }
        RenderBuffer.addStack(value, scale(options), resolveColor(options, "backgroundColor", currentState.backgroundColor()),
                offset(options, "offsetX"), offset(options, "offsetY"), false, drawAir);
    }

    private static void drawItem(String id, LuaTableValue options) {
        Identifier identifier = Identifier.tryParse(id);
        Item item = identifier == null ? Items.AIR : Registries.ITEM.get(identifier);
        RenderBuffer.addItem(new ItemStack(item), scale(options), resolveColor(options, "backgroundColor", currentState.backgroundColor()),
                offset(options, "offsetX"), offset(options, "offsetY"), false);
    }

    private static void drawTexture(String path, LuaTableValue options) {
        Identifier texture = Identifier.tryParse(path);
        if (texture == null) {
            throw new IllegalArgumentException("Invalid texture path: " + path);
        }
        RenderBuffer.addTexture(texture, scale(options), resolveColor(options, "backgroundColor", currentState.backgroundColor()),
                offset(options, "offsetX"), offset(options, "offsetY"), false);
    }

    private static float scale(LuaTableValue options) {
        return scale(options, currentState.scale());
    }

    private static float scale(LuaTableValue options, float fallback) {
        double requested = options == null ? fallback : options.number("scale", fallback);
        if (!Double.isFinite(requested) || requested <= 0 || requested > Float.MAX_VALUE) {
            throw new IllegalArgumentException("Scale must be finite and greater than zero");
        }
        return (float) requested;
    }

    private static int offset(LuaTableValue options, String key) {
        return options == null ? 0 : options.integer(key, 0);
    }

    private static int resolveColor(LuaTableValue options, String key, int fallback) {
        return options != null && options.contains(key) ? color(options.get(key)) : fallback;
    }

    static int color(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String hex = String.valueOf(value);
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() == 6) hex = "FF" + hex;
        return (int) Long.parseLong(hex, 16);
    }
}
