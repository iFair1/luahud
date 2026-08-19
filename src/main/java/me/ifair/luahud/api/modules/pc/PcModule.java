package me.ifair.luahud.api.modules.pc;

import com.sun.management.OperatingSystemMXBean;
import me.ifair.luahud.api.core.LazyTable;
import me.ifair.luahud.api.core.ApiModule;
import net.minecraft.client.MinecraftClient;
import org.luaj.vm2.LuaTable;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GraphicsCard;

import java.lang.management.ManagementFactory;
import java.util.List;

public class PcModule implements ApiModule {
    private final SystemInfo systemInfo;

    public PcModule() {
        this.systemInfo = new SystemInfo();
    }

    @Override
    public void register(LuaTable lh) {
        LazyTable pc = new LazyTable();

        pc.addTable("cpu", new CpuProvider(systemInfo).build());
        // TODO: Gpu
        // TODO: Memory
        // TODO: Display

        lh.set("pc", pc.build());
    }
}
