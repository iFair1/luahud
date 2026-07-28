package me.ifair.luahud.api;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.luaj.vm2.Lua;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

public class WorldModule implements ApiModule {
    @Override
    public void register(LuaTable lh) {
        LuaTable worldTable = new LuaTable();
        lh.set("world", worldTable);
        registerMethods(worldTable);
    }

    private static void registerMethods(LuaTable table) {

        table.set("getX", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                PlayerEntity player = MinecraftClient.getInstance().player;
                return player != null ? LuaValue.valueOf(player.getX()) : LuaValue.ZERO;
            }
        });

        table.set("getY", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                PlayerEntity player = MinecraftClient.getInstance().player;
                return player != null ? LuaValue.valueOf(player.getY()) : LuaValue.ZERO;
            }
        });

        table.set("getZ", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                PlayerEntity player = MinecraftClient.getInstance().player;
                return player != null ? LuaValue.valueOf(player.getZ()) : LuaValue.ZERO;
            }
        });
    }
}
