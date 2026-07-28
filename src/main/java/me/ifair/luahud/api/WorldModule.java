package me.ifair.luahud.api;

import net.minecraft.entity.player.PlayerEntity;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public class WorldModule implements ApiModule {

    @Override
    public void register(LuaTable lh) {
        LuaTable posTable = new LuaTable();
        lh.set("pos", posTable);
    }

    public static void updatePositions(LuaTable lh, PlayerEntity player) {
        // Return early if lh table hasn't loaded yet, instead of fucking crashing
        if (lh == null) return;

        LuaValue posValue = lh.get("pos");
        if (posValue.istable()) {
            LuaTable posTable = (LuaTable) posValue;
            posTable.set("x", LuaValue.valueOf(player.getX()));
            posTable.set("y", LuaValue.valueOf(player.getY()));
            posTable.set("z", LuaValue.valueOf(player.getZ()));
        }
    }
}