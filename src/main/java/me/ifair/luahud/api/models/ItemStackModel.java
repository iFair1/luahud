package me.ifair.luahud.api.models;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import me.ifair.luahud.api.core.LazyTable;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.Map;
import java.util.function.Supplier;

public class ItemStackModel extends LazyTable {
    public ItemStackModel(Supplier<ItemStack> itemStackSupplier) {
        addString("id", () -> Registries.ITEM.getId(itemStackSupplier.get().getItem()).toString());
        addString("name", () -> itemStackSupplier.get().getName().getString());
        addInt("count", () -> itemStackSupplier.get().getCount());

        addInt("damage", () -> itemStackSupplier.get().getDamage());
        addInt("damageLeft", () -> itemStackSupplier.get().getMaxDamage() - itemStackSupplier.get().getDamage());
        addInt("maxDamage", () -> itemStackSupplier.get().getMaxDamage());

        addLazyTable("enchantments", () -> buildEnchantmentsList(itemStackSupplier.get()));
    }

    private LuaTable buildEnchantmentsList(ItemStack itemStack) {
        LuaTable enchantmentsList = new LuaTable();

        ItemEnchantmentsComponent enchantmentsComponent = itemStack.getEnchantments();

        if (enchantmentsComponent.isEmpty()) return enchantmentsList;

        int index = 1;

        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : enchantmentsComponent.getEnchantmentEntries()) {
            RegistryEntry<Enchantment> registryEntry = entry.getKey();
            int level = entry.getIntValue();

            LuaTable singleEnchTable = new LuaTable();

            String enchId = registryEntry.getKey()
                    .map(key -> key.getValue().toString())
                    .orElse("");

            singleEnchTable.set("id", LuaValue.valueOf(enchId));
            singleEnchTable.set("level", LuaValue.valueOf(level));

            enchantmentsList.set(index, singleEnchTable);
            index++;
        }

        return enchantmentsList;
    }
}
