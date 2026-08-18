package me.ifair.luahud.api.models;

import me.ifair.luahud.api.core.LazyTable;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.util.function.Supplier;

public class BlockModel extends LazyTable {
    public BlockModel(Supplier<ClientWorld> worldSupplier, Supplier<BlockPos> posSupplier) {
        addString("id", () -> {
            ClientWorld world = worldSupplier.get();
            BlockPos pos = posSupplier.get();

            if (world == null || pos == null) return "minecraft:air";

            BlockState state = world.getBlockState(pos);
            return Registries.BLOCK.getId(state.getBlock()).toString();
        });

        addString("name", () -> {
            ClientWorld world = worldSupplier.get();
            BlockPos pos = posSupplier.get();

            if (world == null || pos == null) return "Air";
            return world.getBlockState(pos).getBlock().getName().getString();
        });

        addInt("x", () -> {
            BlockPos pos = posSupplier.get();
            return pos != null ? pos.getX() : 0;
        });
        addInt("y", () -> {
            BlockPos pos = posSupplier.get();
            return pos != null ? pos.getY() : 0;
        });
        addInt("z", () -> {
            BlockPos pos = posSupplier.get();
            return pos != null ? pos.getZ() : 0;
        });

        //  TODO: Add blockstates
    }
}
