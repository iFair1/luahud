package me.ifair.luahud.api.modules.pc;

import me.ifair.luahud.api.core.LazyTable;
import me.ifair.luahud.api.core.ApiModule;
import org.luaj.vm2.LuaTable;
import oshi.SystemInfo;

public class PcModule implements ApiModule {
    private final SystemInfo systemInfo;

    public PcModule() {
        this.systemInfo = new SystemInfo();
    }

    @Override
    public void register(LuaTable lh) {
        LazyTable pc = new LazyTable();

        pc.addTable("cpu", new CpuProvider(systemInfo).build());
        pc.addTable("gpu", new GpuProvider(systemInfo).build());
        pc.addTable("memory", new MemoryProvider().build());
        pc.addTable("display", new DisplayProvider().build());

        lh.set("pc", pc.build());
    }
}
