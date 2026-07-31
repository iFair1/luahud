package me.ifair.luahud.api;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.TwoArgFunction;

public class WorldModule implements ApiModule {
    @Override
    public void register(LuaTable lh) {
        MinecraftClient mc = MinecraftClient.getInstance();
        LazyTable world = new LazyTable();

        world.addDouble("x", () -> {
            PlayerEntity player = mc.player;
            return player != null ? player.getX() : 0.0;
        });

        world.addDouble("y", () -> {
            PlayerEntity player = mc.player;
            return player != null ? player.getY() : 0.0;
        });

        world.addDouble("z", () -> {
            PlayerEntity player = mc.player;
            return player != null ? player.getZ() : 0.0;
        });

        lh.set("world", world.build());
    }
}