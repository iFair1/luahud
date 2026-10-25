package dev.ifair.luahud.api.models;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import dev.ifair.luahud.api.LuaApiRegistrar;
import dev.ifair.luahud.api.LuaType;
import dev.ifair.luahud.api.ModelBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.passive.AbstractDonkeyEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
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
        ModelBuilder<StatusEffectInstance> statusEffect,
        ModelBuilder<AbstractHorseEntity> horse
) {
    public record BlockView(ClientWorld world, BlockPos pos) {}

    public static CoreModels register(LuaApiRegistrar api) {
        ModelBuilder<StatusEffectInstance> statusEffect = registerStatusEffect(api);
        ModelBuilder<Entity> entity = api.model("Entity", Entity.class);
        ModelBuilder<AbstractHorseEntity> horse = registerHorse(api, entity);
        registerEntityFields(entity, statusEffect, horse);

        ModelBuilder<BlockView> block = api.model("PlacedBlock", BlockView.class);
        block.field("id", RegistryIds.BLOCK, "Namespaced block ID", CoreModels::blockId);
        block.string("name", "Localized block name", CoreModels::blockName);
        block.integer("x", "Block X coordinate", value -> value.pos() == null ? 0 : value.pos().getX());
        block.integer("y", "Block Y coordinate", value -> value.pos() == null ? 0 : value.pos().getY());
        block.integer("z", "Block Z coordinate", value -> value.pos() == null ? 0 : value.pos().getZ());

        return new CoreModels(block, entity, registerItemStack(api), statusEffect, horse);
    }

    private static ModelBuilder<StatusEffectInstance> registerStatusEffect(LuaApiRegistrar api) {
        ModelBuilder<StatusEffectInstance> model = api.model("StatusEffect", StatusEffectInstance.class);
        var id = model.field("id", RegistryIds.STATUS_EFFECT, "Namespaced status effect ID",
                value -> Registries.STATUS_EFFECT.getId(value.getEffectType().value()).toString()).inputRequired();
        model.string("name", "Localized effect name", value -> value.getEffectType().value().getName().getString());
        var level = model.integer("level", "Effect level; defaults to 1 for input tables",
                value -> value.getAmplifier() + 1).inputDefault(1);
        var seconds = model.integer("durationInSeconds", "Remaining duration in seconds (-1 if infinite)",
                effect -> durationInSeconds(effect.getDuration())).inputOptional();
        var ticks = model.integer("durationInTicks", "Remaining duration in ticks (-1 if infinite); takes precedence over seconds for input tables",
                StatusEffectInstance::getDuration).inputOptional();
        var showParticles = model.bool("showParticles", "Whether effect particles are shown; defaults to true for input tables",
                StatusEffectInstance::shouldShowParticles).inputDefault(true);
        model.fromFields(fields -> {
            StatusEffect effect = registryValue(Registries.STATUS_EFFECT, fields.get(id), "status effect");
            Integer durationTicks;
            if (fields.isExplicit(ticks)) {
                durationTicks = fields.get(ticks);
            } else if (fields.isExplicit(seconds)) {
                durationTicks = durationInTicks(fields.get(seconds));
            } else {
                durationTicks = fields.get(ticks);
                if (durationTicks == null) {
                    Integer durationSeconds = fields.get(seconds);
                    durationTicks = durationSeconds == null ? 0 : durationInTicks(durationSeconds);
                }
            }
            int effectLevel = fields.get(level);
            if (effectLevel < 1) throw new IllegalArgumentException("Effect level must be at least 1");
            if (durationTicks < StatusEffectInstance.INFINITE) throw new IllegalArgumentException("Effect duration must be -1 or non-negative");
            StatusEffectInstance source = fields.source();
            return new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(effect), durationTicks, effectLevel - 1,
                    source != null && source.isAmbient(), fields.get(showParticles),
                    source == null || source.shouldShowIcon());
        });
        return model;
    }

    private static void registerEntityFields(ModelBuilder<Entity> model, ModelBuilder<StatusEffectInstance> statusEffect,
                                             ModelBuilder<AbstractHorseEntity> horse) {
        model.field("id", RegistryIds.ENTITY_TYPE, "Namespaced entity type", value -> Registries.ENTITY_TYPE.getId(value.getType()).toString());
        model.number("x", "X coordinate", Entity::getX);
        model.number("y", "Y coordinate", Entity::getY);
        model.number("z", "Z coordinate", Entity::getZ);
        model.string("name", "Localized display name", value -> value.getDisplayName() == null ? "" : value.getDisplayName().getString());
        var uuid = model.string("uuid", "World entity UUID", Entity::getUuidAsString).inputRequired();
        model.integer("health", "Current health, or 0 for non-living entities", value -> value instanceof LivingEntity living ? (int) living.getHealth() : 0);
        model.integer("maxHealth", "Maximum health, or 0 for non-living entities", value -> value instanceof LivingEntity living ? (int) living.getMaxHealth() : 0);
        model.field("effects", LuaType.map(RegistryIds.STATUS_EFFECT, statusEffect.type()), "Active status effects keyed by namespaced ID", CoreModels::statusEffects);
        model.bool("isLiving", "Whether the entity is a living entity", value -> value instanceof LivingEntity);
        model.bool("isBaby", "Whether the entity is a baby", value -> value instanceof LivingEntity living && living.isBaby());
        model.optionalField("horse", horse.type(), "Horse-family properties, or nil for other entities", CoreModels::horse);
        model.fromFields(fields -> entity(fields.get(uuid)));
    }

    private static ModelBuilder<AbstractHorseEntity> registerHorse(LuaApiRegistrar api, ModelBuilder<Entity> entity) {
        ModelBuilder<AbstractHorseEntity> model = api.model("Horse", AbstractHorseEntity.class);
        model.number("movementSpeed", "Effective movement-speed attribute (not blocks per second)",
                //? if <1.21.2 {
                value -> value.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
                //?} else {
                /*value -> value.getAttributeValue(EntityAttributes.MOVEMENT_SPEED));
                *///?}
        model.number("jumpStrength", "Effective jump-strength attribute (not jump height)",
                //? if <1.21.2 {
                value -> value.getAttributeValue(EntityAttributes.GENERIC_JUMP_STRENGTH));
                //?} else {
                /*value -> value.getAttributeValue(EntityAttributes.JUMP_STRENGTH));
                *///?}
        //? if <1.21.5 {
        model.bool("isSaddled", "Whether a saddle is equipped", value -> value.isSaddled());
        //?} else {
        /*model.bool("isSaddled", "Whether a saddle is equipped", value -> !value.getEquippedStack(net.minecraft.entity.EquipmentSlot.SADDLE).isEmpty());
        *///?}
        model.bool("isTamed", "Whether the animal is tamed", value -> value.isTame());
        model.optionalField("rider", entity.type(), "First passenger, or nil when unoccupied", Entity::getFirstPassenger);
        model.bool("hasChest", "Whether a chest is attached", value -> value instanceof AbstractDonkeyEntity donkey && donkey.hasChest());
        return model;
    }

    /** Includes horses, donkeys, mules, and other AbstractHorseEntity species. */
    public static AbstractHorseEntity horse(Entity entity) {
        return entity instanceof AbstractHorseEntity horse ? horse : null;
    }

    private static ModelBuilder<ItemStack> registerItemStack(LuaApiRegistrar api) {
        ModelBuilder<ItemStack> model = api.model("ItemStack", ItemStack.class);
        var id = model.field("id", RegistryIds.ITEM, "Namespaced item ID", value -> Registries.ITEM.getId(value.getItem()).toString()).inputRequired();
        model.string("name", "Localized item name", value -> value.getName().getString());
        var count = model.integer("count", "Stack count; defaults to 1 for input tables", ItemStack::getCount).inputDefault(1);
        var damage = model.optionalInteger("damage", "Current item damage", value -> value.isDamageable() ? value.getDamage() : null).inputOptional();
        var damageLeft = model.optionalInteger("damageLeft", "Remaining durability", value -> value.isDamageable() ? value.getMaxDamage() - value.getDamage() : null).inputOptional();
        model.optionalInteger("maxDamage", "Maximum durability", value -> value.isDamageable() ? value.getMaxDamage() : null);
        var enchantments = model.optionalField("enchantments", LuaType.map(RegistryIds.ENCHANTMENT, LuaType.integer()), "Enchantments keyed by namespaced ID", CoreModels::enchantments).inputOptional();
        model.fromFields(fields -> {
            Item item = registryValue(Registries.ITEM, fields.get(id), "item");
            ItemStack source = fields.source();
            ItemStack stack = source != null && source.isOf(item) ? source.copy() : new ItemStack(item);
            stack.setCount(fields.get(count));
            Integer itemDamage;
            if (fields.isExplicit(damage)) {
                itemDamage = fields.get(damage);
            } else if (fields.isExplicit(damageLeft)) {
                itemDamage = stack.getMaxDamage() - fields.get(damageLeft);
            } else if (source != null && source.isOf(item)) {
                itemDamage = null;
            } else {
                itemDamage = fields.get(damage);
                if (itemDamage == null) {
                    Integer remaining = fields.get(damageLeft);
                    if (remaining != null) itemDamage = stack.getMaxDamage() - remaining;
                }
            }
            if (itemDamage != null) stack.setDamage(itemDamage);
            if (source == null || !source.isOf(item) || fields.isExplicit(enchantments)) {
                if (fields.isExplicit(enchantments)) stack.set(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
                addEnchantments(stack, fields.get(enchantments));
            }
            return stack;
        });
        return model;
    }

    private static String blockId(BlockView value) {
        if (value.world() == null || value.pos() == null) return "minecraft:air";
        return Registries.BLOCK.getId(value.world().getBlockState(value.pos()).getBlock()).toString();
    }

    private static String blockName(BlockView value) {
        if (value.world() == null || value.pos() == null) return "Air";
        return value.world().getBlockState(value.pos()).getBlock().getName().getString();
    }

    private static Entity entity(String uuidValue) {
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

    private static int durationInTicks(int seconds) {
        return seconds == StatusEffectInstance.INFINITE ? StatusEffectInstance.INFINITE : Math.multiplyExact(seconds, 20);
    }

    private static int durationInSeconds(int ticks) {
        return ticks == StatusEffectInstance.INFINITE ? StatusEffectInstance.INFINITE : ticks / 20;
    }

    private static void addEnchantments(ItemStack stack, Map<String, Integer> enchantments) {
        if (enchantments == null || enchantments.isEmpty()) return;
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) throw new IllegalStateException("Cannot resolve enchantments without a loaded world");
        //? if <1.21.2 {
        Registry<Enchantment> registry = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        //?} else {
        /*Registry<Enchantment> registry = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        *///?}
        for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
            String id = entry.getKey();
            Identifier identifier = identifier(id, "enchantment");
            RegistryEntry.Reference<Enchantment> enchantment = registry.getEntry(identifier)
                    .orElseThrow(() -> new IllegalArgumentException("Enchantment not found: " + id));
            stack.addEnchantment(enchantment, entry.getValue());
        }
    }

    private static <T> T registryValue(Registry<T> registry, String id, String kind) {
        Identifier identifier = identifier(id, kind);
        //? if <1.21.2 {
        return registry.getOrEmpty(identifier)
        //?} else {
        /*return registry.getOptionalValue(identifier)
        *///?}
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
