package me.ifair.luahud.api.core;

import me.ifair.luahud.api.modules.*;
import org.luaj.vm2.LuaTable;

import java.util.ArrayList;
import java.util.List;

public class ApiRegistry {
    private static final List<ApiModule> MODULES = new ArrayList<>();

    static {
        MODULES.add(new RenderModule());

        MODULES.add(new PcModule());
        MODULES.add(new PerformanceModule());
        MODULES.add(new TimeModule());
        MODULES.add(new FacingModule());
    }

    public static void registerAll(LuaTable lh) {
        for (ApiModule module : MODULES) {
            module.register(lh);
        }
    }
}
