package dev.ifair.luahud.api.models;

import dev.ifair.luahud.api.LuaType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;

import java.util.List;

/** Registry-backed ID annotations. Registry contents are read when the stub is generated. */
public final class RegistryIds {
    public static final LuaType<String> ITEM = LuaType.stringAlias("ItemId", "Namespaced item registry ID.",
            () -> Registries.ITEM.getIds().stream().map(Object::toString).toList());
    public static final LuaType<String> BLOCK = LuaType.stringAlias("BlockId", "Namespaced block registry ID.",
            () -> Registries.BLOCK.getIds().stream().map(Object::toString).toList());
    public static final LuaType<String> ENTITY_TYPE = LuaType.stringAlias("EntityTypeId", "Namespaced entity type registry ID.",
            () -> Registries.ENTITY_TYPE.getIds().stream().map(Object::toString).toList());
    public static final LuaType<String> STATUS_EFFECT = LuaType.stringAlias("StatusEffectId", "Namespaced status effect registry ID.",
            () -> Registries.STATUS_EFFECT.getIds().stream().map(Object::toString).toList());

    public static final LuaType<String> ENCHANTMENT = LuaType.stringAlias("EnchantmentId", "Namespaced enchantment registry ID. Available IDs depend on the loaded world and its data packs.", RegistryIds::enchantmentIds);

    private static List<String> enchantmentIds() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.world != null) {
            //? if <1.21.2 {
            return client.world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getIds().stream().map(Object::toString).toList();
            //?} else {
            /*return client.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getIds().stream().map(Object::toString).toList();
            *///?}
        }
        return VanillaEnchantments.IDS;
    }

    private static final class VanillaEnchantments {
        private static final List<String> IDS = BuiltinRegistries.createWrapperLookup()
                //? if <1.21.2 {
                .getWrapperOrThrow(RegistryKeys.ENCHANTMENT)
                //?} else {
                /*.getOrThrow(RegistryKeys.ENCHANTMENT)
                *///?}
                .streamKeys().map(key -> key.getValue().toString()).toList();
    }

    private RegistryIds() {}
}
