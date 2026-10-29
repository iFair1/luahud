package dev.ifair.luahud.api.modules;

import dev.ifair.luahud.api.*;
import dev.ifair.luahud.api.models.CoreModels;
import dev.ifair.luahud.api.models.RegistryIds;
import dev.ifair.luahud.render.Anchor;
import dev.ifair.luahud.render.CanvasTransform;
import dev.ifair.luahud.render.InlineAlignment;
import dev.ifair.luahud.render.InlineDirection;
import dev.ifair.luahud.render.RenderBuffer;
import dev.ifair.luahud.render.RowAlignment;
import dev.ifair.luahud.render.RowDirection;
import dev.ifair.luahud.render.SectionState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public final class RenderModule {
    private static final int ENTITY_CACHE_SIZE = 128;
    private static final Map<UUID, Integer> ENTITY_IDS = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID, Integer> eldest) {
            return size() > ENTITY_CACHE_SIZE;
        }
    };
    private static SectionState currentState = SectionState.defaults();
    private static SectionState canvasPreviousState;
    private record InvocationState(SectionState section, SectionState canvasPrevious) {}
    private static final Deque<InvocationState> INVOCATION_STATES = new ArrayDeque<>();
    private static ClientWorld entityCacheWorld;

    private RenderModule() {}

    public static void resetSection() {
        currentState = SectionState.defaults();
        canvasPreviousState = null;
        RenderBuffer.selectDefault(currentState.layout());
    }

    public static void beginInvocation(Object owner) {
        INVOCATION_STATES.push(new InvocationState(currentState, canvasPreviousState));
        RenderBuffer.beginInvocation(owner);
        resetSection();
    }

    public static void endInvocation() {
        RenderBuffer.endInvocation();
        InvocationState previous = INVOCATION_STATES.pop();
        currentState = previous.section();
        canvasPreviousState = previous.canvasPrevious();
    }

    static SectionState currentSectionState() {
        return currentState;
    }

    public static void register(LuaApiRegistrar api, CoreModels models) {
        LuaType<Object> color = LuaType.union(LuaType.string(), LuaType.integer());
        LuaType<LuaTableValue> sectionOptions = optionsType("SectionOptions", color, true, true, false, false);
        LuaType<LuaTableValue> textOptions = optionsType("TextOptions", color, false, true, false, false);
        LuaType<LuaTableValue> iconOptions = optionsType("IconOptions", color, false, false, false, false);
        LuaType<LuaTableValue> textureOptions = textureOptions(color);
        LuaType<LuaTableValue> stackOptions = optionsType("StackOptions", color, false, false, true, false);
        LuaType<LuaTableValue> entityOptions = optionsType("EntityOptions", color, false, false, false, true);

        registerCanvas(api, color);
        CanvasModule.register(api, color);

        FunctionBuilder<Void> section = api.root().function(
                "section", LuaType.nil(), "Start an independent drawing section with fresh options.");
        LuaParameter<LuaTableValue> sectionArg = section.optionalParameter("options", sectionOptions, "Section options");
        section.call(arguments -> {
            applySection(arguments.get(sectionArg));
            return null;
        });

        FunctionBuilder<Void> newline = api.root().function(
                "newline", LuaType.nil(), "End the current row, optionally repeating the line break.");
        LuaParameter<Integer> newlineCount = newline.optionalParameter(
                "count", LuaType.integer(), "Number of line breaks; defaults to 1");
        newline.call(arguments -> {
            Integer count = arguments.get(newlineCount);
            RenderBuffer.newLine(count == null ? 1 : count);
            return null;
        });

        registerDraw(api, "draw", false, textOptions);
        registerDraw(api, "drawln", true, textOptions);

        FunctionBuilder<Void> drawStack = api.root().function("drawStack", LuaType.nil(), "Draw an item stack.");
        LuaParameter<ItemStack> stackArg = drawStack.parameter("stack", models.itemStack().inputType(), "Item stack");
        LuaParameter<LuaTableValue> stackOpts = drawStack.optionalParameter("options", stackOptions, "Rendering options");
        drawStack.call(arguments -> {
            drawStack(arguments.get(stackArg), arguments.get(stackOpts));
            return null;
        });

        FunctionBuilder<Void> drawItem = api.root().function(
                "drawItem", LuaType.nil(), "Draw an item by namespaced ID or ItemStack model.");
        LuaParameter<Object> itemId = drawItem.parameter(
                "id", LuaType.union(RegistryIds.ITEM, models.itemStack().inputType()), "Namespaced item ID or ItemStack model");
        LuaParameter<LuaTableValue> itemOpts = drawItem.optionalParameter("options", iconOptions, "Rendering options");
        drawItem.call(arguments -> {
            drawItem(arguments.get(itemId), arguments.get(itemOpts));
            return null;
        });

        FunctionBuilder<Void> drawEntity = api.root().function(
                "drawEntity", LuaType.nil(), "Draw a loaded entity by UUID or a table with a uuid field (including lh.player).");
        LuaParameter<Object> entityUuid = drawEntity.parameter(
                "uuid", LuaType.union(LuaType.string(), models.entity().inputType()), "Entity UUID or any table with a string uuid field");
        LuaParameter<LuaTableValue> entityOpts = drawEntity.optionalParameter("options", entityOptions, "Rendering options");
        drawEntity.call(arguments -> {
            drawEntity(arguments.get(entityUuid), arguments.get(entityOpts));
            return null;
        });

        FunctionBuilder<Void> drawTexture = api.root().function("drawTexture", LuaType.nil(), "Draw a texture by namespaced path.");
        LuaParameter<String> texturePath = drawTexture.parameter("path", LuaType.string(), "Namespaced texture path");
        LuaParameter<LuaTableValue> textureOpts = drawTexture.optionalParameter("options", textureOptions, "Rendering options");
        drawTexture.call(arguments -> {
            drawTexture(arguments.get(texturePath), arguments.get(textureOpts));
            return null;
        });
    }

    public static void clearTextureCache() { CanvasModule.clearImageSizes(); }

    private static void canvasFields(LuaType.InputTableBuilder fields, boolean text) {
        fields.optional("x", LuaType.number(), "Canvas-local X; defaults to 0")
                .optional("y", LuaType.number(), "Canvas-local Y; defaults to 0")
                .optional("rotation", LuaType.number(), "Canvas-only clockwise 2D rotation in degrees")
                .optional("originX", LuaType.number(), "Canvas-only rotation pivot X relative to the element")
                .optional("originY", LuaType.number(), "Canvas-only rotation pivot Y relative to the element");
        if (text) fields.optional("scale", LuaType.number(), "Canvas-only text scale; defaults to 1");
    }

    private static LuaType<LuaTableValue> textureOptions(LuaType<Object> color) {
        var region = LuaType.inputTable("TextureRegion", fields -> fields
                .field("x", LuaType.integer(), "Source X in pixels")
                .field("y", LuaType.integer(), "Source Y in pixels")
                .field("width", LuaType.integer(), "Source width in pixels")
                .field("height", LuaType.integer(), "Source height in pixels"));
        var slice = LuaType.inputTable("NineSlice", fields -> fields
                .field("left", LuaType.integer(), "Left source border in pixels")
                .field("right", LuaType.integer(), "Right source border in pixels")
                .field("top", LuaType.integer(), "Top source border in pixels")
                .field("bottom", LuaType.integer(), "Bottom source border in pixels"));
        return LuaType.inputTable("TextureOptions", fields -> {
            optionsType("TextureBase", color, false, false, false, false).inputFields().values()
                    .forEach(field -> fields.field(field.name(), field.type(), field.doc(), field.optional()));
            fields.optional("width", LuaType.number(), "Canvas target width; defaults to source region width")
                    .optional("height", LuaType.number(), "Canvas target height; defaults to source region height")
                    .optional("region", region, "Canvas-only source rectangle")
                    .optional("nineSlice", slice, "Canvas-only nine-slice borders; incompatible with region");
        });
    }

    private static void registerCanvas(LuaApiRegistrar api, LuaType<Object> color) {
        var options = LuaType.inputTable("CanvasOptions", fields -> {
            fields.field("width", LuaType.number(), "Positive local width")
                    .field("height", LuaType.number(), "Positive local height")
                    .optional("inline", LuaType.bool(), "Insert into the current section; defaults to false")
                    .optional("clip", LuaType.bool(), "Clip to the canvas bounds; defaults to true");
            var sectionFields = optionsType("CanvasSectionBase", color, true, true, false, false).inputFields();
            for (String key : java.util.List.of("anchor", "offsetX", "offsetY", "scale", "opacity", "id", "duration", "visible",
                    "textColor", "shadow")) {
                var field = sectionFields.get(key);
                fields.field(field.name(), field.type(), field.doc(), field.optional());
            }
        });
        var canvas = api.root().function("canvas", LuaType.nil(),
                "Select a fixed-size 2D canvas. A new canvas closes the previous one; nesting is not supported.");
        var opts = canvas.parameter("options", options, "Canvas placement and dimensions");
        canvas.call(args -> {
            LuaTableValue o = args.get(opts);
            float width = CanvasModule.length(o, "width", 0);
            float height = CanvasModule.length(o, "height", 0);
            if (width == 0 || height == 0) throw new IllegalArgumentException("Canvas dimensions must be greater than zero");
            boolean inline = o.bool("inline", false);
            if (inline) {
                for (String key : java.util.List.of("anchor", "offsetX", "offsetY", "id", "duration", "visible")) {
                    if (o.contains(key)) throw new IllegalArgumentException(key + " is not supported by inline canvases");
                }
            }
            SectionState canvasState = sectionState(o);
            closeCanvas();
            SectionState previous = currentState;
            RenderBuffer.beginCanvas(canvasState.layout(), width, height, inline ? canvasState.scale() : 1,
                    o.bool("clip", true), o.bool("visible", true), inline,
                    o.string("id", null), o.contains("duration") ? o.number("duration", 0) : null);
            canvasPreviousState = previous;
            // Canvas content has no implicit row background or icon scale.
            currentState = sectionState(new LuaTableValue(java.util.Map.of(
                    "backgroundColor", 0, "textColor", canvasState.textColor(), "shadow", canvasState.shadow(),
                    "opacity", canvasState.opacity() * (inline ? previous.opacity() : 1))));
            return null;
        });
        var end = api.root().function("endCanvas", LuaType.nil(), "Close the canvas and resume the previous section.");
        end.call(args -> {
            if (!RenderBuffer.isCanvas()) throw new IllegalStateException("No canvas is selected");
            closeCanvas();
            return null;
        });
    }

    private static void closeCanvas() {
        if (!RenderBuffer.isCanvas()) return;
        RenderBuffer.endCanvas();
        currentState = canvasPreviousState;
        canvasPreviousState = null;
    }

    private static CanvasTransform prepareDraw(LuaTableValue options) {
        RenderBuffer.opacity(currentState.opacity() * opacity(options));
        if (!RenderBuffer.isCanvas()) {
            if (options != null) {
                for (String key : java.util.List.of("x", "y", "rotation", "originX", "originY")) {
                    if (options.contains(key)) throw new IllegalArgumentException(key + " requires a canvas");
                }
            }
            return CanvasTransform.IDENTITY;
        }
        if (newLine(options)) throw new IllegalArgumentException("Canvas does not support automatic line breaks");
        CanvasTransform transform = new CanvasTransform(
                CanvasModule.number(options, "x", 0) + offset(options, "offsetX"),
                CanvasModule.number(options, "y", 0) + offset(options, "offsetY"),
                CanvasModule.number(options, "scale", 1), CanvasModule.number(options, "rotation", 0),
                CanvasModule.number(options, "originX", 0), CanvasModule.number(options, "originY", 0));
        RenderBuffer.canvasTransform(transform);
        return transform;
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

    private static LuaType<LuaTableValue> optionsType(
            String name,
            LuaType<Object> color,
            boolean section,
            boolean text,
            boolean stack,
            boolean entity
    ) {
        return LuaType.inputTable(name, fields -> {
            if (!section) canvasFields(fields, text);
            fields.optional("opacity", LuaType.number(), "Opacity from 0 to 1; multiplies section opacity and color alpha; defaults to 1")
                    .optional("backgroundColor", color, "ARGB integer or RGB/ARGB hex string")
                    .optional("offsetX", LuaType.integer(), section
                            ? "Horizontal inset for edge anchors or signed shift for center anchors"
                            : "Local horizontal offset")
                    .optional("offsetY", LuaType.integer(), section ? "Vertical screen-edge inset" : "Local vertical offset");
            if (section) {
                fields.optional("id", LuaType.string(), "Shared section ID; replaces any existing section with this ID")
                        .optional("visible", LuaType.bool(), "Whether this section is drawn; defaults to true")
                        .optional("duration", LuaType.number(), "Positive lifetime in seconds; omitted sections last until this callback runs again")
                        .optional("anchor", LuaType.union(
                                LuaType.literal("topLeft"), LuaType.literal("topCenter"), LuaType.literal("topRight"),
                                LuaType.literal("bottomLeft"), LuaType.literal("bottomCenter"), LuaType.literal("bottomRight")),
                                "Screen anchor")
                        .optional("rowAlign", LuaType.union(
                                LuaType.literal("left"), LuaType.literal("center"), LuaType.literal("right")),
                                "Alignment of rows within the widest row; defaults from the anchor")
                        .optional("inlineAlign", LuaType.union(
                                LuaType.literal("top"), LuaType.literal("center"), LuaType.literal("bottom")),
                                "Vertical alignment of elements within a row; defaults to center")
                        .optional("inlineDirection", LuaType.union(
                                LuaType.literal("leftToRight"), LuaType.literal("rightToLeft")),
                                "Visual order of elements within a row; defaults to leftToRight")
                        .optional("rowDirection", LuaType.union(
                                LuaType.literal("topToBottom"), LuaType.literal("bottomToTop")),
                                "Visual order of rows within the section; defaults to topToBottom")
                        .optional("iconScale", LuaType.number(),
                                "Default item, stack, entity, and texture scale relative to the section")
                        .optional("rowHeight", LuaType.union(LuaType.integer(), LuaType.literal("auto")),
                                "Fixed unscaled row height, or auto to fit the tallest element; defaults to auto")
                        .optional("lineSpacing", LuaType.integer(),
                                "Unscaled background spacing around each row, favoring the top; defaults to 2")
                        .optional("inlineSpacing", LuaType.integer(),
                                "Unscaled background spacing between visible elements; defaults to 0");
            }
            if (section || text) {
                fields.optional("textColor", color, "Text color")
                        .optional("shadow", LuaType.bool(), "Draw text shadow");
            }
            if (section || !text) {
                fields.optional("scale", LuaType.number(),
                        section ? "Scale applied to the entire section" : "Scale relative to the section");
            }
            if (!section && !text) {
                fields.optional("newLine", LuaType.bool(), "Advance to the next line after drawing; defaults to false");
            }
            if (stack) {
                fields.optional("showDurabilityBar", LuaType.bool(), "Show durability bar")
                        .optional("showCount", LuaType.bool(), "Show stack count")
                        .optional("showGlint", LuaType.bool(), "Show enchantment glint")
                        .optional("drawAir", LuaType.bool(), "Reserve one item width for an empty stack");
            }
            if (entity) {
                fields.optional("yaw", LuaType.number(), "Horizontal viewing angle offset in degrees")
                        .optional("pitch", LuaType.number(), "Vertical viewing angle offset in degrees")
                        .optional("fullBright", LuaType.bool(), "Use maximum lighting; defaults to true")
                        .optional("useEntityRotation", LuaType.bool(),
                                "Use the entity's current rotation as the base for yaw and pitch offsets");
            }
        });
    }

    private static void applySection(LuaTableValue options) {
        closeCanvas();
        SectionState state = sectionState(options);
        String id = options == null ? null : options.string("id", null);
        Double duration = options == null || !options.contains("duration") ? null : options.number("duration", 0);
        if (options != null && !options.bool("visible", true)) {
            RenderBuffer.hideSection(id);
            currentState = state;
            return;
        }
        RenderBuffer.beginSection(state.layout(), id, duration);
        currentState = state;
    }

    static SectionState sectionState(LuaTableValue options) {
        SectionState defaults = SectionState.defaults();
        Anchor anchor = options == null ? defaults.anchor() : Anchor.fromString(options.string("anchor", null));
        int offsetX = options == null ? anchor.defaultOffsetX() : options.integer("offsetX", anchor.defaultOffsetX());
        int offsetY = options == null ? defaults.offsetY() : options.integer("offsetY", defaults.offsetY());
        RowAlignment rowAlignment = options == null || !options.contains("rowAlign")
                ? anchor.defaultRowAlignment()
                : RowAlignment.fromString(options.string("rowAlign", null));
        InlineAlignment inlineAlignment = options == null || !options.contains("inlineAlign")
                ? defaults.inlineAlignment()
                : InlineAlignment.fromString(options.string("inlineAlign", null));
        InlineDirection inlineDirection = options == null || !options.contains("inlineDirection")
                ? defaults.inlineDirection()
                : InlineDirection.fromString(options.string("inlineDirection", null));
        RowDirection rowDirection = options == null || !options.contains("rowDirection")
                ? defaults.rowDirection()
                : RowDirection.fromString(options.string("rowDirection", null));
        RowHeight rowHeight = rowHeight(options, defaults);

        return new SectionState(
                anchor,
                offsetX,
                offsetY,
                rowAlignment,
                inlineAlignment,
                inlineDirection,
                rowDirection,
                options == null ? defaults.shadow() : options.bool("shadow", defaults.shadow()),
                options != null && options.contains("textColor") ? color(options.get("textColor")) : defaults.textColor(),
                options != null && options.contains("backgroundColor")
                        ? color(options.get("backgroundColor")) : defaults.backgroundColor(),
                scale(options, "scale", defaults.scale()),
                scale(options, "iconScale", defaults.iconScale()),
                rowHeight.value(),
                rowHeight.auto(),
                options == null ? defaults.lineSpacing() : options.integer("lineSpacing", defaults.lineSpacing()),
                options == null ? defaults.inlineSpacing() : options.integer("inlineSpacing", defaults.inlineSpacing()),
                opacity(options)
        );
    }

    private record RowHeight(int value, boolean auto) {}

    private static RowHeight rowHeight(LuaTableValue options, SectionState defaults) {
        if (options == null || !options.contains("rowHeight")) {
            return new RowHeight(defaults.rowHeight(), defaults.autoRowHeight());
        }
        Object requested = options.get("rowHeight");
        if ("auto".equals(requested)) return new RowHeight(defaults.rowHeight(), true);
        if (requested instanceof Number number) return new RowHeight(number.intValue(), false);
        throw new IllegalArgumentException("Row height must be a positive integer or 'auto'");
    }

    private static void drawText(String text, LuaTableValue options, boolean newline) {
        prepareDraw(options);
        if (!RenderBuffer.isCanvas() && options != null && options.contains("scale")) {
            throw new IllegalArgumentException("Text scale requires a canvas");
        }
        if (RenderBuffer.isCanvas() && newline) throw new IllegalArgumentException("Canvas does not support line breaks");
        RenderBuffer.addText(text,
                resolveColor(options, "textColor", currentState.textColor()),
                resolveColor(options, "backgroundColor", currentState.backgroundColor()),
                options == null ? currentState.shadow() : options.bool("shadow", currentState.shadow()),
                offset(options, "offsetX"), offset(options, "offsetY"), newline);
    }

    private static void drawStack(ItemStack stack, LuaTableValue options) {
        prepareDraw(options);
        boolean showCount = options == null || options.bool("showCount", true);
        boolean showDurability = options == null || options.bool("showDurabilityBar", true);
        boolean drawAir = options != null && options.bool("drawAir", false);
        ItemStack value = stack.copy();
        if (!showCount) value.setCount(1);
        if (!showDurability) value.setDamage(0);
        if (options != null && options.contains("showGlint")) {
            value.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, options.bool("showGlint", true));
        }
        RenderBuffer.addStack(value, iconScale(options), resolveColor(options, "backgroundColor", currentState.backgroundColor()),
                offset(options, "offsetX"), offset(options, "offsetY"), newLine(options), drawAir);
    }

    private static void drawItem(Object reference, LuaTableValue options) {
        prepareDraw(options);
        Item item = reference instanceof ItemStack stack
                ? stack.getItem()
                : Registries.ITEM.get(itemIdentifier((String) reference, Registries.ITEM::containsId));
        RenderBuffer.addItem(new ItemStack(item), iconScale(options), resolveColor(options, "backgroundColor", currentState.backgroundColor()),
                offset(options, "offsetX"), offset(options, "offsetY"), newLine(options));
    }

    static Identifier itemIdentifier(String id, Predicate<Identifier> itemExists) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            throw new IllegalArgumentException("Invalid item ID: " + id);
        }
        if (!itemExists.test(identifier)) {
            throw new IllegalArgumentException("Item not found: " + id);
        }
        return identifier;
    }

    private static void drawEntity(Object reference, LuaTableValue options) {
        prepareDraw(options);
        Entity entity = reference instanceof Entity value ? value : findEntity((String) reference);
        RenderBuffer.addEntity(entity, iconScale(options), angle(options, "yaw"), angle(options, "pitch"),
                bool(options, "fullBright", true), bool(options, "useEntityRotation", false),
                resolveColor(options, "backgroundColor", currentState.backgroundColor()),
                offset(options, "offsetX"), offset(options, "offsetY"), newLine(options));
    }

    private static Entity findEntity(String value) {
        UUID uuid;
        try {
            uuid = UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid entity UUID: " + value, error);
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) throw new IllegalStateException("Cannot draw an entity without a loaded world");
        if (entityCacheWorld != client.world) {
            entityCacheWorld = client.world;
            ENTITY_IDS.clear();
        }

        Integer entityId = ENTITY_IDS.get(uuid);
        if (entityId != null) {
            Entity cached = client.world.getEntityById(entityId);
            if (cached != null && cached.getUuid().equals(uuid)) return cached;
            ENTITY_IDS.remove(uuid);
        }
        for (Entity entity : client.world.getEntities()) {
            if (entity.getUuid().equals(uuid)) {
                ENTITY_IDS.put(uuid, entity.getId());
                return entity;
            }
        }
        throw new IllegalArgumentException("No loaded entity found for UUID: " + value);
    }

    private static void drawTexture(String path, LuaTableValue options) {
        CanvasTransform transform = prepareDraw(options);
        MinecraftClient client = MinecraftClient.getInstance();
        Identifier texture = textureIdentifier(path,
                identifier -> client.getResourceManager().getResource(identifier).isPresent());
        if (RenderBuffer.isCanvas()) {
            CanvasModule.drawImage(texture, options, transform);
            return;
        }
        if (options != null && (options.contains("width") || options.contains("height")
                || options.contains("region") || options.contains("nineSlice"))) {
            throw new IllegalArgumentException("Texture size, region and nineSlice require a canvas");
        }
        RenderBuffer.addTexture(texture, iconScale(options), resolveColor(options, "backgroundColor", currentState.backgroundColor()),
                offset(options, "offsetX"), offset(options, "offsetY"), newLine(options));
    }

    static Identifier textureIdentifier(String path, Predicate<Identifier> resourceExists) {
        Identifier texture = Identifier.tryParse(path);
        if (texture == null) {
            throw new IllegalArgumentException("Invalid texture path: " + path);
        }
        if (!resourceExists.test(texture)) {
            throw new IllegalArgumentException("Texture not found: " + path);
        }
        return texture;
    }

    private static float iconScale(LuaTableValue options) {
        return RenderBuffer.isCanvas() ? 1 : scale(options, "scale", currentState.iconScale());
    }

    static float angle(LuaTableValue options, String key) {
        double requested = options == null ? 0 : options.number(key, 0);
        if (!Double.isFinite(requested)) throw new IllegalArgumentException("Angle must be finite");
        return (float) (requested % 360);
    }

    static boolean bool(LuaTableValue options, String key, boolean fallback) {
        return options == null ? fallback : options.bool(key, fallback);
    }

    static boolean newLine(LuaTableValue options) {
        return bool(options, "newLine", false);
    }

    static float opacity(LuaTableValue options) {
        double value = options == null ? 1 : options.number("opacity", 1);
        if (!Double.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException("Opacity must be finite and between 0 and 1");
        return (float) value;
    }

    private static float scale(LuaTableValue options, String key, float fallback) {
        double requested = options == null ? fallback : options.number(key, fallback);
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
