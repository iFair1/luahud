package me.ifair.luahud.api.modules.pc;

import com.sun.management.OperatingSystemMXBean;
import me.ifair.luahud.api.core.CachedValue;
import me.ifair.luahud.api.core.LazyTable;
import org.luaj.vm2.LuaTable;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

import java.lang.management.ManagementFactory;
import java.util.function.IntSupplier;

public class CpuProvider {
    private final CentralProcessor processor;
    private final OperatingSystemMXBean osBean;
    private final CachedValue<Integer> usageCache;

    public CpuProvider(SystemInfo systemInfo) {
        this.processor = systemInfo.getHardware().getProcessor();
        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        this.usageCache = new CachedValue<>(1000, this::fetchUsage);
    }

    public LuaTable build() {
        LazyTable cpu = new LazyTable();

        // Getting these once
        final String cpuName = processor.getProcessorIdentifier().getName();
        final int coreCount = processor.getPhysicalProcessorCount();
        final int threadCount = processor.getLogicalProcessorCount();

        cpu.addString("name", () -> cpuName);
        cpu.addInt("cores", () -> coreCount);
        cpu.addInt("threads", () -> threadCount);
        cpu.addInt("usage", (IntSupplier) usageCache);

        return cpu.build();
    }

    private int fetchUsage() {
        double load = osBean.getProcessCpuLoad();
        if (load < 0) return 0;
        return (int) Math.round(load * 100);
    }
}
