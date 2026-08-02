package me.ifair.luahud.api;

import com.sun.management.OperatingSystemMXBean;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.server.MinecraftServer;
import org.luaj.vm2.LuaTable;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GraphicsCard;

import java.lang.management.ManagementFactory;
import java.util.List;


public class PerformanceModule implements ApiModule {
    @Override
    public void register(LuaTable lh) {
        MinecraftClient client = MinecraftClient.getInstance();

        LazyTable performance = new LazyTable();
        performance.addInt("fps", client::getCurrentFps);
        performance.addInt("ping", this::getPing);
        performance.addDouble("tps", this::getTps);

        SystemInfo systemInfo = new SystemInfo();
        CentralProcessor processor = systemInfo.getHardware().getProcessor();
        LazyTable cpu = new LazyTable();

        cpu.addString("name", () -> processor.getProcessorIdentifier().getName());
        cpu.addInt("cores", processor::getPhysicalProcessorCount);
        cpu.addInt("threads", processor::getLogicalProcessorCount);
        cpu.addInt("usage", () -> {
            OperatingSystemMXBean OsBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

            double load = OsBean.getProcessCpuLoad();

            if (load < 0) return 0;

            return (int) Math.round(load * 100);
        });


        LazyTable gpu = new LazyTable();
        gpu.addString("name", () -> {
            List<GraphicsCard> cards = systemInfo.getHardware().getGraphicsCards();
            if (!cards.isEmpty()) {
                return cards.get(0).getName();
            }
            return "Unknown GPU";

        });
        gpu.addInt("usage", () -> {
            // TODO: Copy some code to make this work
            return 69;
        });

        Runtime runtime = Runtime.getRuntime();
        LazyTable memory = new LazyTable();
        memory.addInt("allocated", () -> {
            long allocatedBytes = runtime.totalMemory();
            return (int) (allocatedBytes >> 20); // 1 Byte >> 20 is 1 MiB
        });
        memory.addInt("free", () -> {
            long freeBytes = runtime.freeMemory();
            return (int) (freeBytes >> 20);
        });
        memory.addInt("used", () -> {
            long allocatedBytes = runtime.totalMemory();
            long freeBytes = runtime.freeMemory();
            return (int) ((allocatedBytes - freeBytes) >> 20);
        });
        memory.addInt("total", () -> {
            long maxBytes = runtime.maxMemory();
            return (int) (maxBytes >> 20);
        });

        memory.addInt("allocatedPercentage", () -> {
            long maxBytes = runtime.maxMemory();
            long allocatedBytes = runtime.totalMemory();

            return (int) (allocatedBytes * 100L / maxBytes);
        });
        memory.addInt("freePercentage", () -> {
            long maxBytes = runtime.maxMemory();
            long freeBytes = runtime.freeMemory();

            return (int) (freeBytes * 100L / maxBytes);
        });
        memory.addInt("usedPercentage", () -> {
            long maxBytes = runtime.maxMemory();
            long usedBytes = runtime.totalMemory() - runtime.freeMemory();

            return (int) (usedBytes * 100L / maxBytes);
        });

        performance.addTable("cpu", cpu.build());
        performance.addTable("gpu", gpu.build());
        performance.addTable("memory", memory.build());
        lh.set("perf", performance.build());
    }

    private int getPing() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null || client.getNetworkHandler() == null) {
            return 0;
        }

        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return entry != null ? entry.getLatency() : 0;
    }

    private double getTps() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client == null || client.getServer() == null) {
            return 0.0;
        }

        MinecraftServer server = client.getServer();
        float mspt = server.getAverageTickTime();

        if (mspt <= 0) {
            return 0.0;
        }

        double tps = 1000.0 / mspt;

        return Math.min(20.0, tps);
    }
}