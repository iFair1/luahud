package dev.ifair.luahud.api.models;

import dev.ifair.luahud.api.LuaApiRegistrar;
import dev.ifair.luahud.api.ModelBuilder;
import net.minecraft.registry.Registries;

import dev.ifair.luahud.api.models.CoreModels.BlockView;
import static dev.ifair.luahud.api.models.ModelRegistryValues.*;

public final class PlacedBlockModel {
    static ModelBuilder<BlockView> register(LuaApiRegistrar api) {
        ModelBuilder<BlockView> block = api.model("PlacedBlock", BlockView.class);
        block.field("id", RegistryIds.BLOCK, "Namespaced block ID", PlacedBlockModel::blockId);
        block.string("name", "Localized block name", PlacedBlockModel::blockName);
        block.integer("x", "Block X coordinate", value -> value.pos() == null ? 0 : value.pos().getX());
        block.integer("y", "Block Y coordinate", value -> value.pos() == null ? 0 : value.pos().getY());
        block.integer("z", "Block Z coordinate", value -> value.pos() == null ? 0 : value.pos().getZ());
        return block;
    }

    static String blockId(BlockView value) {
        if (value.world() == null || value.pos() == null) return "minecraft:air";
        return Registries.BLOCK.getId(value.world().getBlockState(value.pos()).getBlock()).toString();
    }

    static String blockName(BlockView value) {
        if (value.world() == null || value.pos() == null) return "Air";
        return value.world().getBlockState(value.pos()).getBlock().getName().getString();
    }
}
