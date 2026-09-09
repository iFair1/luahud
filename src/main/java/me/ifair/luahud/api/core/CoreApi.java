package me.ifair.luahud.api.core;

import com.mojang.brigadier.StringReader;
import com.sun.management.OperatingSystemMXBean;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import me.ifair.luahud.LuaHUD;
import me.ifair.luahud.api.*;
import me.ifair.luahud.render.Anchor;
import me.ifair.luahud.render.RenderBuffer;
import me.ifair.luahud.render.SectionState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.argument.PosArgument;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.world.biome.Biome;
import oshi.PlatformEnum;
import oshi.SystemInfo;
import oshi.hardware.*;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/** Registers LuaHUD's own API using the same public builder workflow exposed to addons. */
public final class CoreApi {
    private static final long TICKS_PER_DAY = 24000L, TICKS_PER_HOUR = 1000L, TIME_OFFSET = 6000L;
    private static final long MIB = 1024 * 1024;
    private static SectionState currentState = new SectionState();

    private record BlockView(ClientWorld world, BlockPos pos) {}

    private CoreApi() {}

    public static void resetSection() { currentState = new SectionState(); }

    public static void register(ApiRegistry registry) {
        LuaApiRegistrar api = registry.registrar("luahud");
        MinecraftClient client = MinecraftClient.getInstance();

        ModelBuilder<Entity> entity = api.model("Entity", Entity.class)
                .number("x", "X coordinate", Entity::getX)
                .number("y", "Y coordinate", Entity::getY)
                .number("z", "Z coordinate", Entity::getZ)
                .string("id", "Namespaced entity type", value -> Registries.ENTITY_TYPE.getId(value.getType()).toString())
                .string("name", "Localized display name", value -> value.getDisplayName() == null ? "" : value.getDisplayName().getString())
                .string("uuid", "World entity UUID", Entity::getUuidAsString)
                .number("health", "Current health, or 0 for non-living entities", value -> value instanceof LivingEntity living ? (double) living.getHealth() : 0.0)
                .number("maxHealth", "Maximum health, or 0 for non-living entities", value -> value instanceof LivingEntity living ? (double) living.getMaxHealth() : 0.0);
        ModelBuilder<BlockView> block = api.model("PlacedBlock", BlockView.class)
                .string("id", "Namespaced block ID", CoreApi::blockId)
                .string("name", "Localized block name", CoreApi::blockName)
                .integer("x", "Block X coordinate", value -> value.pos() == null ? 0 : value.pos().getX())
                .integer("y", "Block Y coordinate", value -> value.pos() == null ? 0 : value.pos().getY())
                .integer("z", "Block Z coordinate", value -> value.pos() == null ? 0 : value.pos().getZ());
        ModelBuilder<ItemStack> stack = api.model("ItemStack", ItemStack.class)
                .string("id", "Namespaced item ID", value -> Registries.ITEM.getId(value.getItem()).toString())
                .string("name", "Localized item name", value -> value.getName().getString())
                .integer("count", "Stack count", ItemStack::getCount)
                .integer("damage", "Current item damage", ItemStack::getDamage)
                .integer("damageLeft", "Remaining durability", value -> value.getMaxDamage() - value.getDamage())
                .integer("maxDamage", "Maximum durability", ItemStack::getMaxDamage)
                .field("enchantments", LuaType.optional(LuaType.map(LuaType.integer())), "Enchantments keyed by namespaced ID", CoreApi::enchantments);

        registerPerformance(api, client);
        registerPc(api, client);
        registerTime(api, client);
        registerWeather(api, client);
        registerPosition(api, client);
        registerFacing(api, client, block, entity);
        registerPlayer(api, client, stack);
        registerFunctions(api, client, block, stack);
        registry.addEventFunctions();
    }

    private static void registerPerformance(LuaApiRegistrar api, MinecraftClient client) {
        api.root().namespace("perf", perf -> perf
                .integer("fps", "Current rendered frames per second", client::getCurrentFps)
                .integer("ping", "Player latency in milliseconds", CoreApi::ping)
                .number("tps", "Integrated-server ticks per second", CoreApi::tps));
    }

    private static void registerPc(LuaApiRegistrar api, MinecraftClient client) {
        SystemInfo info = new SystemInfo();
        CentralProcessor processor = info.getHardware().getProcessor();
        List<GraphicsCard> cards = info.getHardware().getGraphicsCards();
        OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        CachedValue<Integer> cpuUsage = new CachedValue<>(1000, () -> {
            double load = os.getProcessCpuLoad(); return load < 0 ? 0 : (int) Math.round(load * 100);
        });
        CachedValue<Integer> gpuUsage = new CachedValue<>(2000, () -> gpuUsage(info));
        Runtime runtime = Runtime.getRuntime();
        api.root().namespace("pc", pc -> {
            pc.namespace("cpu", cpu -> cpu
                    .string("name", "Processor name", () -> processor.getProcessorIdentifier().getName())
                    .integer("cores", "Physical core count", processor::getPhysicalProcessorCount)
                    .integer("threads", "Logical processor count", processor::getLogicalProcessorCount)
                    .integer("usage", "Minecraft process CPU usage percent", cpuUsage));
            pc.namespace("gpu", gpu -> gpu
                    .string("name", "Primary graphics card name", () -> cards.isEmpty() ? "Unknown GPU" : cards.getFirst().getName())
                    .integer("usage", "GPU usage percent, or -1 when unavailable", gpuUsage));
            pc.namespace("memory", memory -> memory
                    .integer("used", "Memory used in MiB", () -> (int) ((runtime.totalMemory() - runtime.freeMemory()) / MIB))
                    .integer("allocated", "Memory allocated in MiB", () -> (int) (runtime.totalMemory() / MIB))
                    .integer("max", "Maximum memory in MiB", () -> (int) (runtime.maxMemory() / MIB))
                    .integer("free", "Memory still available in MiB", () -> (int) ((runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / MIB)));
            pc.namespace("display", display -> display
                    .integer("width", "Window width", () -> client.getWindow().getWidth())
                    .integer("height", "Window height", () -> client.getWindow().getHeight())
                    .integer("scale", "GUI scale", () -> (int) client.getWindow().getScaleFactor())
                    .integer("hz", "Monitor refresh rate", () -> client.getWindow().getRefreshRate()));
        });
    }

    private static void registerTime(LuaApiRegistrar api, MinecraftClient client) {
        api.root().namespace("time", time -> time
                .number("tick", "Tick within the current Minecraft day", () -> (double) (worldTime(client) % TICKS_PER_DAY))
                .integer("day", "Days elapsed in the world", () -> (int) (worldTime(client) / TICKS_PER_DAY))
                .integer("hour", "Minecraft hour", () -> (int) (shiftedTime(client) / TICKS_PER_HOUR))
                .integer("minute", "Minecraft minute", () -> (int) ((shiftedTime(client) % TICKS_PER_HOUR) * 60 / TICKS_PER_HOUR))
                .integer("second", "Minecraft second", () -> (int) (((shiftedTime(client) % TICKS_PER_HOUR) * 3600 / TICKS_PER_HOUR) % 60)));
    }

    private static void registerWeather(LuaApiRegistrar api, MinecraftClient client) {
        api.root().namespace("weather", weather -> weather
                .bool("raining", "Whether rain falls at the player", () -> precipitation(client, Biome.Precipitation.RAIN))
                .bool("snowing", "Whether snow falls at the player", () -> precipitation(client, Biome.Precipitation.SNOW))
                .bool("thundering", "Whether the world is thundering", () -> client.world != null && client.world.isThundering()));
    }

    private static void registerPosition(LuaApiRegistrar api, MinecraftClient client) {
        api.root().namespace("pos", pos -> {
            pos.namespace("biome", biome -> biome
                    .string("id", "Namespaced biome ID", () -> client.world == null || client.player == null ? "" : client.world.getBiome(client.player.getBlockPos()).getIdAsString())
                    .string("name", "Localized biome name", () -> biomeName(client)));
            pos.namespace("dimension", dimension -> dimension.string("id", "Namespaced dimension ID", () -> client.world == null ? "" : client.world.getRegistryKey().getValue().toString()));
        });
    }

    private static void registerFacing(LuaApiRegistrar api, MinecraftClient client, ModelBuilder<BlockView> block, ModelBuilder<Entity> entity) {
        api.root().namespace("facing", facing -> facing
                .string("direction", "Horizontal facing direction", () -> client.player == null ? "" : client.player.getHorizontalFacing().asString())
                .number("yaw", "Wrapped yaw in degrees", () -> client.player == null ? 0.0 : (double) MathHelper.wrapDegrees(client.player.getYaw()))
                .number("pitch", "Pitch in degrees", () -> client.player == null ? 0.0 : (double) client.player.getPitch())
                .value("targetBlock", LuaType.optional(block.type()), LuaDoc.of("Block under the crosshair"), () -> targetBlock(client))
                .value("targetEntity", LuaType.optional(entity.type()), LuaDoc.of("Entity under the crosshair"), () -> targetEntity(client)));
    }

    private static void registerPlayer(LuaApiRegistrar api, MinecraftClient client, ModelBuilder<ItemStack> stack) {
        api.root().namespace("player", player -> {
            player.string("id", "Namespaced player entity type", () -> client.player == null ? "" : Registries.ENTITY_TYPE.getId(client.player.getType()).toString())
                    .string("name", "Player display name", () -> client.player == null ? "" : client.player.getDisplayName().getString())
                    .string("uuid", "Player UUID", () -> client.player == null ? "" : client.player.getUuidAsString())
                    .number("x", "X coordinate", () -> client.player == null ? 0.0 : client.player.getX())
                    .number("y", "Y coordinate", () -> client.player == null ? 0.0 : client.player.getY())
                    .number("z", "Z coordinate", () -> client.player == null ? 0.0 : client.player.getZ())
                    .number("health", "Current health", () -> client.player == null ? 0.0 : (double) client.player.getHealth())
                    .number("maxHealth", "Maximum health", () -> client.player == null ? 0.0 : (double) client.player.getMaxHealth())
                    .string("username", "Account username", () -> client.player == null ? "" : client.player.getGameProfile().getName())
                    .bool("sprinting", "Whether the character is sprinting", () -> client.player != null && client.player.isSprinting())
                    .bool("sprintHeld", "Whether the sprint key is held", () -> client.options.sprintKey.isPressed())
                    .string("gamemode", "Current game mode", () -> gameMode(client));
            player.namespace("hunger", hunger -> hunger
                    .number("food", "Food level", () -> client.player == null ? 0.0 : (double) client.player.getHungerManager().getFoodLevel())
                    .number("saturation", "Saturation level", () -> client.player == null ? 0.0 : (double) client.player.getHungerManager().getSaturationLevel()));
            player.namespace("armor", armor -> armor
                    .number("armor", "Armor attribute", () -> attribute(client, EntityAttributes.GENERIC_ARMOR))
                    .number("toughness", "Armor toughness attribute", () -> attribute(client, EntityAttributes.GENERIC_ARMOR_TOUGHNESS))
                    .number("knockbackResistance", "Explosion knockback resistance", () -> attribute(client, EntityAttributes.GENERIC_EXPLOSION_KNOCKBACK_RESISTANCE)));
            player.namespace("xp", xp -> xp
                    .integer("total", "Total experience points", () -> client.player == null ? 0 : client.player.totalExperience)
                    .integer("level", "Experience level", () -> client.player == null ? 0 : client.player.experienceLevel)
                    .number("progress", "Progress toward the next level", () -> client.player == null ? 0.0 : (double) client.player.experienceProgress));
            player.namespace("slots", slots -> slots
                    .model("head", stack, "Head armor stack", () -> inventoryStack(client, 39))
                    .model("chest", stack, "Chest armor stack", () -> inventoryStack(client, 38))
                    .model("legs", stack, "Leg armor stack", () -> inventoryStack(client, 37))
                    .model("feet", stack, "Feet armor stack", () -> inventoryStack(client, 36))
                    .model("mainHand", stack, "Main-hand stack", () -> client.player == null ? ItemStack.EMPTY : client.player.getInventory().getMainHandStack())
                    .model("offHand", stack, "Off-hand stack", () -> client.player == null ? ItemStack.EMPTY : client.player.getOffHandStack()));
        });
    }

    private static void registerFunctions(LuaApiRegistrar api, MinecraftClient client, ModelBuilder<BlockView> block, ModelBuilder<ItemStack> stack) {
        FunctionBuilder<BlockView> getBlock = api.root().function("getBlockAt", LuaType.optional(block.type()), "Get the block at absolute, ~, or ^ coordinates.");
        LuaParameter<String> coordinate = getBlock.parameter("coordinate", LuaType.string(), "Minecraft coordinate expression");
        getBlock.call(args -> { BlockPos pos = resolveCoordinates(args.get(coordinate), client.player); return pos == null ? null : new BlockView(client.world, pos); });

        LuaType<Object> color = LuaType.union(LuaType.string(), LuaType.integer());
        LuaType<LuaTableValue> sectionOptions = optionsType("SectionOptions", color, true, true, false);
        LuaType<LuaTableValue> textOptions = optionsType("TextOptions", color, false, true, false);
        LuaType<LuaTableValue> iconOptions = optionsType("IconOptions", color, false, false, false);
        LuaType<LuaTableValue> stackOptions = optionsType("StackOptions", color, false, false, true);

        FunctionBuilder<Void> section = api.root().function("section", LuaType.nil(), "Reset drawing state and apply section options.");
        LuaParameter<LuaTableValue> sectionArg = section.optionalParameter("options", sectionOptions, "Section options");
        section.call(args -> { applySection(args.get(sectionArg)); return null; });

        registerDraw(api, "draw", false, textOptions);
        registerDraw(api, "drawln", true, textOptions);

        FunctionBuilder<Void> drawStack = api.root().function("drawStack", LuaType.nil(), "Draw an item stack.");
        LuaParameter<LuaTableValue> stackArg = drawStack.parameter("stack", itemStackInput(), "Stack data");
        LuaParameter<LuaTableValue> stackOpts = drawStack.optionalParameter("options", stackOptions, "Rendering options");
        drawStack.call(args -> { drawStack(args.get(stackArg), args.get(stackOpts)); return null; });

        FunctionBuilder<Void> drawItem = api.root().function("drawItem", LuaType.nil(), "Draw an item by namespaced ID.");
        LuaParameter<String> itemId = drawItem.parameter("id", LuaType.string(), "Namespaced item ID");
        LuaParameter<LuaTableValue> itemOpts = drawItem.optionalParameter("options", iconOptions, "Rendering options");
        drawItem.call(args -> { drawItem(args.get(itemId), args.get(itemOpts)); return null; });

        FunctionBuilder<Void> drawTexture = api.root().function("drawTexture", LuaType.nil(), "Draw a texture by namespaced path.");
        LuaParameter<String> texturePath = drawTexture.parameter("path", LuaType.string(), "Namespaced texture path");
        LuaParameter<LuaTableValue> textureOpts = drawTexture.optionalParameter("options", iconOptions, "Rendering options");
        drawTexture.call(args -> { drawTexture(args.get(texturePath), args.get(textureOpts)); return null; });
    }

    private static void registerDraw(LuaApiRegistrar api, String name, boolean newline, LuaType<LuaTableValue> type) {
        FunctionBuilder<Void> draw = api.root().function(name, LuaType.nil(), newline ? "Draw text and advance to the next line." : "Draw text.");
        LuaParameter<Object> text = draw.parameter("text", LuaType.any(), "Value converted to text");
        LuaParameter<LuaTableValue> options = draw.optionalParameter("options", type, "Text options");
        draw.call(args -> { drawText(String.valueOf(args.get(text)), args.get(options), newline); return null; });
    }

    private static LuaType<LuaTableValue> optionsType(String name, LuaType<Object> color, boolean section, boolean text, boolean stack) {
        return LuaType.inputTable(name, fields -> {
            fields.optional("backgroundColor", color, "ARGB integer or RGB/ARGB hex string")
                    .optional("offsetX", LuaType.integer(), "Horizontal offset")
                    .optional("offsetY", LuaType.integer(), "Vertical offset");
            if (section) fields.optional("anchor", LuaType.union(LuaType.literal("topLeft"), LuaType.literal("bottomLeft"), LuaType.literal("topRight"), LuaType.literal("bottomRight")), "Screen anchor");
            if (section || text) fields.optional("textColor", color, "Text color").optional("shadow", LuaType.bool(), "Draw text shadow");
            if (section || !text) fields.optional("scale", LuaType.number(), "Drawing scale");
            if (stack) fields.optional("showDurabilityBar", LuaType.bool(), "Show durability bar").optional("showCount", LuaType.bool(), "Show stack count").optional("showGlint", LuaType.bool(), "Show enchantment glint");
        });
    }

    private static LuaType<LuaTableValue> itemStackInput() {
        return LuaType.inputTable("ItemStackInput", fields -> fields.field("id", LuaType.string(), "Namespaced item ID")
                .optional("count", LuaType.integer(), "Stack count").optional("damage", LuaType.integer(), "Item damage")
                .optional("enchantments", LuaType.map(LuaType.integer()), "Enchantments"));
    }

    private static void applySection(LuaTableValue options) {
        currentState = new SectionState(); if (options == null) return;
        String anchor = options.string("anchor", null); if (anchor != null) currentState.anchor = Anchor.fromString(anchor);
        if (options.contains("textColor")) currentState.textColor = color(options.get("textColor"));
        if (options.contains("backgroundColor")) currentState.backgroundColor = color(options.get("backgroundColor"));
        currentState.offsetX = options.integer("offsetX", currentState.offsetX); currentState.offsetY = options.integer("offsetY", currentState.offsetY);
        currentState.shadow = options.bool("shadow", currentState.shadow); currentState.scale = (float) options.number("scale", currentState.scale);
    }

    private static void drawText(String text, LuaTableValue options, boolean newline) {
        RenderBuffer.addText(text, resolveColor(options, "textColor", currentState.textColor), resolveColor(options, "backgroundColor", currentState.backgroundColor),
                options == null ? currentState.shadow : options.bool("shadow", currentState.shadow), currentState.anchor, currentState.offsetX, currentState.offsetY, newline);
    }

    private static void drawStack(LuaTableValue stack, LuaTableValue options) {
        boolean count = options == null || options.bool("showCount", true), durability = options == null || options.bool("showDurabilityBar", true);
        boolean glint = options == null ? stack.contains("enchantments") : options.bool("showGlint", stack.contains("enchantments"));
        Identifier id = Identifier.tryParse(stack.string("id", "")); Item item = id == null ? Items.AIR : Registries.ITEM.get(id);
        ItemStack value = new ItemStack(item, count ? stack.integer("count", 1) : 1);
        if (durability && stack.contains("damage")) value.setDamage(stack.integer("damage", 0));
        if (glint) value.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        RenderBuffer.addStack(value, scale(options), resolveColor(options, "backgroundColor", currentState.backgroundColor), currentState.anchor, currentState.offsetX, currentState.offsetY, false);
    }

    private static void drawItem(String id, LuaTableValue options) {
        Identifier identifier = Identifier.tryParse(id); Item item = identifier == null ? Items.AIR : Registries.ITEM.get(identifier);
        RenderBuffer.addItem(new ItemStack(item), scale(options), resolveColor(options, "backgroundColor", currentState.backgroundColor), currentState.anchor, currentState.offsetX, currentState.offsetY, false);
    }

    private static void drawTexture(String path, LuaTableValue options) {
        Identifier texture = Identifier.tryParse(path);
        if (texture == null) { LuaHUD.LOGGER.error("Invalid texture path! Skipping. ({})", path); return; }
        RenderBuffer.addTexture(texture, scale(options), resolveColor(options, "backgroundColor", currentState.backgroundColor), currentState.anchor, currentState.offsetX, currentState.offsetY, false);
    }

    private static float scale(LuaTableValue options) { return options == null ? currentState.scale : (float) options.number("scale", currentState.scale); }
    private static int resolveColor(LuaTableValue options, String key, int fallback) { return options != null && options.contains(key) ? color(options.get(key)) : fallback; }
    private static int color(Object value) { if (value instanceof Number number) { long color = number.longValue(); if ((color & 0xFF000000L) == 0 && color <= 0xFFFFFF) color |= 0xFF000000L; return (int) color; } String hex = String.valueOf(value); if (hex.startsWith("#")) hex = hex.substring(1); if (hex.length() == 6) hex = "FF" + hex; return (int) Long.parseLong(hex, 16); }

    private static String blockId(BlockView value) { if (value.world() == null || value.pos() == null) return "minecraft:air"; return Registries.BLOCK.getId(value.world().getBlockState(value.pos()).getBlock()).toString(); }
    private static String blockName(BlockView value) { if (value.world() == null || value.pos() == null) return "Air"; return value.world().getBlockState(value.pos()).getBlock().getName().getString(); }
    private static Map<String, Integer> enchantments(ItemStack stack) { ItemEnchantmentsComponent component = stack.getEnchantments(); if (component.isEmpty()) return null; Map<String, Integer> result = new LinkedHashMap<>(); for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : component.getEnchantmentEntries()) entry.getKey().getKey().ifPresent(key -> result.put(key.getValue().toString(), entry.getIntValue())); return result; }
    private static int ping() { MinecraftClient c = MinecraftClient.getInstance(); if (c.player == null || c.getNetworkHandler() == null) return 0; PlayerListEntry entry = c.getNetworkHandler().getPlayerListEntry(c.player.getUuid()); return entry == null ? 0 : entry.getLatency(); }
    private static double tps() { MinecraftClient c = MinecraftClient.getInstance(); if (c.getServer() == null) return 0; float mspt = c.getServer().getAverageTickTime(); return mspt <= 0 ? 0 : Math.min(20, 1000.0 / mspt); }
    private static long worldTime(MinecraftClient c) { return c.world == null ? 0 : c.world.getTimeOfDay(); }
    private static long shiftedTime(MinecraftClient c) { return (worldTime(c) + TIME_OFFSET) % TICKS_PER_DAY; }
    private static boolean precipitation(MinecraftClient c, Biome.Precipitation expected) { return c.world != null && c.player != null && c.world.isRaining() && c.world.getBiome(c.player.getBlockPos()).value().getPrecipitation(c.player.getBlockPos()) == expected; }
    private static String biomeName(MinecraftClient c) { if (c.world == null || c.player == null) return ""; return c.world.getBiome(c.player.getBlockPos()).getKey().map(key -> Text.translatable(Util.createTranslationKey("biome", key.getValue())).getString()).orElse(""); }
    private static BlockView targetBlock(MinecraftClient c) { if (c.crosshairTarget instanceof BlockHitResult hit && hit.getType() != HitResult.Type.MISS) return new BlockView(c.world, hit.getBlockPos()); return null; }
    private static Entity targetEntity(MinecraftClient c) { return c.crosshairTarget instanceof EntityHitResult hit ? hit.getEntity() : null; }
    private static double attribute(MinecraftClient c, RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute) { return c.player == null ? 0 : c.player.getAttributeValue(attribute); }
    private static ItemStack inventoryStack(MinecraftClient c, int slot) { return c.player == null ? ItemStack.EMPTY : c.player.getInventory().getStack(slot); }
    private static String gameMode(MinecraftClient c) { if (c.player == null) return ""; PlayerListEntry entry = c.player.networkHandler.getPlayerListEntry(c.player.getUuid()); GameMode mode = entry == null ? null : entry.getGameMode(); return mode == null ? "" : mode.name().toLowerCase(Locale.ROOT); }
    private static BlockPos resolveCoordinates(String value, ClientPlayerEntity player) { try { PosArgument argument = Vec3ArgumentType.vec3().parse(new StringReader(value)); return player == null ? null : argument.toAbsoluteBlockPos(player.getCommandSource()); } catch (Exception error) { LuaHUD.LOGGER.error("{} is not a valid position.", value); return null; } }

    private static int gpuUsage(SystemInfo info) {
        try { if (SystemInfo.getCurrentPlatform() == PlatformEnum.LINUX) { int nvidia = nvidiaUsage(); if (nvidia >= 0) return nvidia; File file = new File("/sys/class/drm/card0/device/gpu_busy_percent"); if (file.exists()) return Integer.parseInt(new String(Files.readAllBytes(Paths.get(file.getAbsolutePath()))).trim()); } else if (SystemInfo.getCurrentPlatform() == PlatformEnum.WINDOWS) return nvidiaUsage(); }
        catch (Exception error) { LuaHUD.LOGGER.error("Error while getting GPU usage", error); }
        return -1;
    }
    private static int nvidiaUsage() { try { Process process = new ProcessBuilder("nvidia-smi", "--query-gpu=utilization.gpu", "--format=csv,noheader,nounits").start(); try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) { String line = reader.readLine(); return line == null ? -1 : Integer.parseInt(line.trim()); } } catch (Exception ignored) { return -1; } }
}
