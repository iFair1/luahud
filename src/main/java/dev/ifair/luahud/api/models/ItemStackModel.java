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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

import static dev.ifair.luahud.api.models.ModelRegistryValues.*;

public final class ItemStackModel {
    static ModelBuilder<ItemStack> register(LuaApiRegistrar api) {
        ModelBuilder<ItemStack> model = api.model("ItemStack", ItemStack.class);
        var id = model.field("id", RegistryIds.ITEM, "Namespaced item ID", value -> Registries.ITEM.getId(value.getItem()).toString()).inputRequired();
        model.string("name", "Localized item name", value -> value.getName().getString());
        var count = model.integer("count", "Stack count; defaults to 1 for input tables", ItemStack::getCount).inputDefault(1);
        model.integer("maxCount", "Maximum stack count", ItemStack::getMaxCount);
        var damage = model.optionalInteger("damage", "Current item damage", value -> value.isDamageable() ? value.getDamage() : null).inputOptional();
        var damageLeft = model.optionalInteger("damageLeft", "Remaining durability", value -> value.isDamageable() ? value.getMaxDamage() - value.getDamage() : null).inputOptional();
        model.optionalInteger("maxDamage", "Maximum durability", value -> value.isDamageable() ? value.getMaxDamage() : null);
        var enchantments = model.optionalField("enchantments", LuaType.map(RegistryIds.ENCHANTMENT, LuaType.integer()), "Enchantments keyed by namespaced ID", ItemStackModel::enchantments).inputOptional();
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

    static void addEnchantments(ItemStack stack, Map<String, Integer> enchantments) {
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

    static Map<String, Integer> enchantments(ItemStack stack) {
        ItemEnchantmentsComponent component = stack.getEnchantments();
        if (component.isEmpty()) return null;

        Map<String, Integer> result = new LinkedHashMap<>();
        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : component.getEnchantmentEntries()) {
            entry.getKey().getKey().ifPresent(key -> result.put(key.getValue().toString(), entry.getIntValue()));
        }
        return result;
    }
}
