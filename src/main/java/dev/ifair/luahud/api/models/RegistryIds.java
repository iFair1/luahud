package dev.ifair.luahud.api.models;

import dev.ifair.luahud.api.LuaType;
import net.minecraft.registry.Registries;

/** Registry-backed ID annotations. Registry contents are read when the stub is generated. */
public final class RegistryIds {
    public static final LuaType<String> ITEM = LuaType.stringAlias("ItemId",
            () -> Registries.ITEM.getIds().stream().map(Object::toString).toList());
    public static final LuaType<String> BLOCK = LuaType.stringAlias("BlockId",
            () -> Registries.BLOCK.getIds().stream().map(Object::toString).toList());
    public static final LuaType<String> ENTITY_TYPE = LuaType.stringAlias("EntityTypeId",
            () -> Registries.ENTITY_TYPE.getIds().stream().map(Object::toString).toList());

    private RegistryIds() {}
}
