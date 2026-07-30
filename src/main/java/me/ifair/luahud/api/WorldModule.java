package me.ifair.luahud.api;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.TwoArgFunction;

public class WorldModule implements ApiModule {
    @Override
    public void register(LuaTable lh) {
        LuaTable worldTable = new LuaTable();
        LuaTable metatable = new LuaTable();

        metatable.set(LuaValue.INDEX, new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue table, LuaValue key) {
                // Only accepts string calls
                if (!key.isstring()) return LuaValue.NIL;

                String varName = key.checkjstring();
                PlayerEntity player = MinecraftClient.getInstance().player;

                if (player == null) return LuaValue.ZERO;

                // Return the wanted var
                switch (varName) {
                    case "x": return LuaValue.valueOf(player.getX());
                    case "y": return LuaValue.valueOf(player.getY());
                    case "z": return LuaValue.valueOf(player.getZ());
                    default: return LuaValue.NIL; // Return nil if lua called an invalid var
                }
            }
        });

        worldTable.setmetatable(metatable);
        lh.set("world", worldTable);
    }
}