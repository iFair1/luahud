package me.ifair.luahud.api.modules;

import com.sun.management.OperatingSystemMXBean;
import me.ifair.luahud.api.core.LazyTable;
import me.ifair.luahud.api.core.ApiModule;
import org.luaj.vm2.LuaTable;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GraphicsCard;

import java.lang.management.ManagementFactory;
import java.util.List;

public class PcModule implements ApiModule {
    private static final Runtime RUNTIME = Runtime.getRuntime();
    private static final long MIB = 1024 * 1024;

    @Override
    public void register(LuaTable lh) {
        LazyTable pc = new LazyTable();



        pc.addTable("cpu", buildCpu());
        pc.addTable("gpu", buildGpu());
        pc.addTable("memory", buildMemory());
        lh.set("pc", pc.build());
    }

    private LuaTable buildCpu() {
        SystemInfo systemInfo = new SystemInfo();
        CentralProcessor processor = systemInfo.getHardware().getProcessor();
        LazyTable cpu = new LazyTable();

        final String CPU_NAME = processor.getProcessorIdentifier().getName();
        final int coreCount = processor.getPhysicalProcessorCount();
        final int threadCount = processor.getLogicalProcessorCount();

        cpu.addString("name", () -> CPU_NAME);
        cpu.addInt("cores", () -> coreCount);
        cpu.addInt("threads", () -> threadCount);
        cpu.addInt("usage", this::getCpuUsage);

        return cpu.build();
    }

    private int getCpuUsage() {
        OperatingSystemMXBean OsBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        double load = OsBean.getProcessCpuLoad();

        if (load < 0) return 0;

        return (int) Math.round(load * 100);
    }

    private LuaTable buildGpu() {
        SystemInfo systemInfo = new SystemInfo();
        List<GraphicsCard> cards = systemInfo.getHardware().getGraphicsCards();

        LazyTable gpu = new LazyTable();

        final String GPU_NAME = !cards.isEmpty() ? cards.getFirst().getName() : "Unknown GPU";

        gpu.addString("name", () -> GPU_NAME);
        gpu.addInt("usage", this::getGpuUsage);

        return gpu.build();
    }

    private int getGpuUsage () {
        // TODO: Make this work
        return 3131;
    }


    private LuaTable buildMemory() {
        LazyTable memory = new LazyTable();

        memory.addInt("used", this::getUsedMemory);
        memory.addInt("allocated", this::getAllocatedMemory);
        memory.addInt("max", this::getMaxMemory);
        memory.addInt("free", this::getFreeMemory);

        return memory.build();
    }

    private int getUsedMemory() {
        return (int) ((RUNTIME.totalMemory() - RUNTIME.freeMemory()) / MIB);
    }

    private int getAllocatedMemory() {
        return (int) (RUNTIME.totalMemory() / MIB);
    }

    private int getMaxMemory() {
        return (int) (RUNTIME.maxMemory() / MIB);
    }

    private int getFreeMemory() {
        long used = RUNTIME.totalMemory() - RUNTIME.freeMemory();
        return (int) ((RUNTIME.maxMemory() - used) / MIB);
    }

}
