package me.ifair.luahud.api.models;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import me.ifair.luahud.api.LuaApiRegistrar;
import me.ifair.luahud.api.LuaType;
import me.ifair.luahud.api.ModelBuilder;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

/** Shared model descriptors used by LuaHUD's built-in modules. */
public record CoreModels(
        ModelBuilder<BlockView> block,
        ModelBuilder<Entity> entity,
        ModelBuilder<ItemStack> itemStack
) {
    public record BlockView(ClientWorld world, BlockPos pos) {}

    public static CoreModels register(LuaApiRegistrar api) {
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
                .string("id", "Namespaced block ID", CoreModels::blockId)
                .string("name", "Localized block name", CoreModels::blockName)
                .integer("x", "Block X coordinate", value -> value.pos() == null ? 0 : value.pos().getX())
                .integer("y", "Block Y coordinate", value -> value.pos() == null ? 0 : value.pos().getY())
                .integer("z", "Block Z coordinate", value -> value.pos() == null ? 0 : value.pos().getZ());

        ModelBuilder<ItemStack> itemStack = api.model("ItemStack", ItemStack.class)
                .string("id", "Namespaced item ID", value -> Registries.ITEM.getId(value.getItem()).toString())
                .string("name", "Localized item name", value -> value.getName().getString())
                .integer("count", "Stack count", ItemStack::getCount)
                .integer("damage", "Current item damage", ItemStack::getDamage)
                .integer("damageLeft", "Remaining durability", value -> value.getMaxDamage() - value.getDamage())
                .integer("maxDamage", "Maximum durability", ItemStack::getMaxDamage)
                .field("enchantments", LuaType.optional(LuaType.map(LuaType.integer())), "Enchantments keyed by namespaced ID", CoreModels::enchantments);

        return new CoreModels(block, entity, itemStack);
    }

    private static String blockId(BlockView value) {
        if (value.world() == null || value.pos() == null) return "minecraft:air";
        return Registries.BLOCK.getId(value.world().getBlockState(value.pos()).getBlock()).toString();
    }

    private static String blockName(BlockView value) {
        if (value.world() == null || value.pos() == null) return "Air";
        return value.world().getBlockState(value.pos()).getBlock().getName().getString();
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
}
