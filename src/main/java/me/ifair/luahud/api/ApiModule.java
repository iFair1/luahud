package me.ifair.luahud.api;

import org.luaj.vm2.LuaTable;

public interface ApiModule {
    void register(LuaTable lh);
}
