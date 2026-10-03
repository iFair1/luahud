package me.ifair.luahud.api.models;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import me.ifair.luahud.api.LuaApiRegistrar;
import me.ifair.luahud.api.LuaTableValue;
import me.ifair.luahud.api.LuaType;
import me.ifair.luahud.api.ModelBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Shared model descriptors used by LuaHUD's built-in modules. */
public record CoreModels(
        ModelBuilder<BlockView> block,
        ModelBuilder<Entity> entity,
        ModelBuilder<ItemStack> itemStack,
        ModelBuilder<StatusEffectInstance> statusEffect
) {
    public record BlockView(ClientWorld world, BlockPos pos) {}

    public static CoreModels register(LuaApiRegistrar api) {
        ModelBuilder<StatusEffectInstance> statusEffect = api.model("StatusEffect", StatusEffectInstance.class)
                .string("id", "Namespaced status effect ID", value -> Registries.STATUS_EFFECT.getId(value.getEffectType().value()).toString())
                .string("name", "Localized effect name", value -> value.getEffectType().value().getName().getString())
                .integer("level", "Effect level", value -> value.getAmplifier() + 1)
                .integer("durationInSeconds", "Remaining duration in seconds (-1 if infinite)", effect -> effect.getDuration() / 20)
                .integer("durationInTicks", "Remaining duration in ticks (-1 if infinite)", StatusEffectInstance::getDuration)
                .fromTable(CoreModels::statusEffect);

        ModelBuilder<Entity> entity = api.model("Entity", Entity.class)
                .string("id", "Namespaced entity type", value -> Registries.ENTITY_TYPE.getId(value.getType()).toString())
                .number("x", "X coordinate", Entity::getX)
                .number("y", "Y coordinate", Entity::getY)
                .number("z", "Z coordinate", Entity::getZ)
                .string("name", "Localized display name", value -> value.getDisplayName() == null ? "" : value.getDisplayName().getString())
                .string("uuid", "World entity UUID", Entity::getUuidAsString)
                .integer("health", "Current health, or 0 for non-living entities", value -> value instanceof LivingEntity living ? (int) living.getHealth() : 0)
                .integer("maxHealth", "Maximum health, or 0 for non-living entities", value -> value instanceof LivingEntity living ? (int) living.getMaxHealth() : 0)
                .optionalField("effects", LuaType.map(statusEffect.type()), "Active status effects keyed by namespaced ID", CoreModels::statusEffects)
                .bool("isLiving", "Whether the entity is a living-entity or not", value -> value instanceof LivingEntity)
                .fromTable(CoreModels::entity);

        ModelBuilder<BlockView> block = api.model("PlacedBlock", BlockView.class)
                .string("id", "Namespaced block ID", CoreModels::blockId)
                .string("name", "Localized block name", CoreModels::blockName)
                .integer("x", "Block X coordinate", value -> value.pos() == null ? 0 : value.pos().getX())
                .integer("y", "Block Y coordinate", value -> value.pos() == null ? 0 : value.pos().getY())
                .integer("z", "Block Z coordinate", value -> value.pos() == null ? 0 : value.pos().getZ())
                .fromTable(CoreModels::block);

        ModelBuilder<ItemStack> itemStack = api.model("ItemStack", ItemStack.class)
                .string("id", "Namespaced item ID", value -> Registries.ITEM.getId(value.getItem()).toString())
                .string("name", "Localized item name", value -> value.getName().getString())
                .integer("count", "Stack count; defaults to 1 for input tables", ItemStack::getCount)
                .optionalInteger("damage", "Current item damage", value -> value.isDamageable() ? value.getDamage() : null)
                .optionalInteger("damageLeft", "Remaining durability", value -> value.isDamageable() ? value.getMaxDamage() - value.getDamage() : null)
                .optionalInteger("maxDamage", "Maximum durability", value -> value.isDamageable() ? value.getMaxDamage() : null)
                .optionalField("enchantments", LuaType.map(LuaType.integer()), "Enchantments keyed by namespaced ID", CoreModels::enchantments)
                .fromTable(CoreModels::itemStack);

        return new CoreModels(block, entity, itemStack, statusEffect);
    }

    private static String blockId(BlockView value) {
        if (value.world() == null || value.pos() == null) return "minecraft:air";
        return Registries.BLOCK.getId(value.world().getBlockState(value.pos()).getBlock()).toString();
    }

    private static String blockName(BlockView value) {
        if (value.world() == null || value.pos() == null) return "Air";
        return value.world().getBlockState(value.pos()).getBlock().getName().getString();
    }

    private static BlockView block(LuaTableValue value) {
        registryValue(Registries.BLOCK, value.string("id", null), "block");
        MinecraftClient client = MinecraftClient.getInstance();
        return new BlockView(client.world, new BlockPos(
                value.integer("x", 0), value.integer("y", 0), value.integer("z", 0)));
    }

    private static Entity entity(LuaTableValue value) {
        registryValue(Registries.ENTITY_TYPE, value.string("id", null), "entity");
        String uuidValue = value.string("uuid", null);
        if (uuidValue == null) throw new IllegalArgumentException("Entity model requires field 'uuid'");
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidValue);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid entity UUID: " + uuidValue, error);
        }
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) throw new IllegalStateException("Cannot use an entity model without a loaded world");
        for (Entity candidate : world.getEntities()) {
            if (candidate.getUuid().equals(uuid)) return candidate;
        }
        throw new IllegalArgumentException("No loaded entity found for UUID: " + uuidValue);
    }

    private static StatusEffectInstance statusEffect(LuaTableValue value) {
        StatusEffect effect = registryValue(Registries.STATUS_EFFECT, value.string("id", null), "status effect");
        RegistryEntry<StatusEffect> entry = Registries.STATUS_EFFECT.getEntry(effect);
        int duration = value.contains("durationInTick")
                ? value.integer("durationInTick", 0)
                : durationInTicks(value.integer("duration", 0));
        return new StatusEffectInstance(entry, duration, value.integer("level", 1) - 1);
    }

    private static int durationInTicks(int seconds) {
        return seconds == StatusEffectInstance.INFINITE ? StatusEffectInstance.INFINITE : seconds * 20;
    }

    private static ItemStack itemStack(LuaTableValue value) {
        Item item = registryValue(Registries.ITEM, value.string("id", null), "item");
        ItemStack stack = new ItemStack(item, value.integer("count", 1));
        if (value.contains("damage")) {
            stack.setDamage(value.integer("damage", 0));
        } else if (value.contains("damageLeft")) {
            stack.setDamage(stack.getMaxDamage() - value.integer("damageLeft", stack.getMaxDamage()));
        }
        addEnchantments(stack, value.get("enchantments"));
        return stack;
    }

    private static void addEnchantments(ItemStack stack, Object value) {
        if (!(value instanceof Map<?, ?> enchantments) || enchantments.isEmpty()) return;
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) throw new IllegalStateException("Cannot resolve enchantments without a loaded world");
        Registry<Enchantment> registry = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        for (Map.Entry<?, ?> entry : enchantments.entrySet()) {
            String id = String.valueOf(entry.getKey());
            Identifier identifier = identifier(id, "enchantment");
            RegistryEntry.Reference<Enchantment> enchantment = registry.getEntry(identifier)
                    .orElseThrow(() -> new IllegalArgumentException("Enchantment not found: " + id));
            stack.addEnchantment(enchantment, ((Number) entry.getValue()).intValue());
        }
    }

    private static <T> T registryValue(Registry<T> registry, String id, String kind) {
        Identifier identifier = identifier(id, kind);
        return registry.getOrEmpty(identifier)
                .orElseThrow(() -> new IllegalArgumentException(capitalize(kind) + " not found: " + id));
    }

    private static Identifier identifier(String id, String kind) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) throw new IllegalArgumentException("Invalid " + kind + " ID: " + id);
        return identifier;
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static Map<String, Integer> enchantments(ItemStack stack) {
        ItemEnchantmentsComponent component = stack.getEnchantments();
        if (component.isEmpty()) return null;

        Map<String, Integer> result = new LinkedHashMap<>();
        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : component.getEnchantmentEntries()) {
            entry.getKey().getKey().ifPresent(key -> result.put(key.getValue().toString(), entry.getIntValue()));
        }
        return result;
    }

    public static Map<String, StatusEffectInstance> statusEffects(Entity entity) {
        Map<String, StatusEffectInstance> result = new LinkedHashMap<>();
        if (!(entity instanceof LivingEntity living)) return result;

        for (StatusEffectInstance effect : living.getStatusEffects()) {
            result.put(Registries.STATUS_EFFECT.getId(effect.getEffectType().value()).toString(), effect);
        }
        return result;
    }
}
