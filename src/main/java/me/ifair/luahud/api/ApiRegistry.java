package me.ifair.luahud.api;

import org.luaj.vm2.LuaTable;

import java.util.ArrayList;
import java.util.List;

public class ApiRegistry {
    private static final List<ApiModule> MODULES = new ArrayList<>();

    static {
        MODULES.add(new RenderModule());
        MODULES.add(new PerformanceModule());
        MODULES.add(new PcModule());
        MODULES.add(new TimeModule());
    }

    public static void registerAll(LuaTable lh) {
        for (ApiModule module : MODULES) {
            module.register(lh);
        }
    }
}
