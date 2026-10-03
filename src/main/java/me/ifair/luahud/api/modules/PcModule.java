package me.ifair.luahud.api.modules;

import com.sun.management.OperatingSystemMXBean;
import me.ifair.luahud.api.LuaApiRegistrar;
import me.ifair.luahud.api.core.CachedValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlTimer;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GraphicsCard;

import java.lang.management.ManagementFactory;
import java.util.List;

public final class PcModule {
    private static final long MIB = 1024 * 1024;

    private PcModule() {}

    private static final class Hardware {
        private static final SystemInfo INFO = new SystemInfo();
        private static final CentralProcessor PROCESSOR = INFO.getHardware().getProcessor();
        private static final List<GraphicsCard> CARDS = INFO.getHardware().getGraphicsCards();
    }

    public static void register(LuaApiRegistrar api, MinecraftClient client) {
        CachedValue<Integer> cpuUsage = new CachedValue<>(1000, () -> {
            OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            double load = os.getProcessCpuLoad();
            return load < 0 ? 0 : (int) Math.round(load * 100);
        });
        CachedValue<Integer> gpuUsage = new CachedValue<>(1000, () -> getGpuUsage(client));
        Runtime runtime = Runtime.getRuntime();

        api.root().namespace("pc", pc -> {
            pc.namespace("cpu", cpu -> cpu
                    .string("name", "Processor name", () -> Hardware.PROCESSOR.getProcessorIdentifier().getName())
                    .integer("cores", "Physical core count", () -> Hardware.PROCESSOR.getPhysicalProcessorCount())
                    .integer("threads", "Logical processor count", () -> Hardware.PROCESSOR.getLogicalProcessorCount())
                    .integer("usage", "Minecraft process CPU usage percent", cpuUsage));
            pc.namespace("gpu", gpu -> gpu
                    .string("name", "Primary graphics card name", () -> Hardware.CARDS.isEmpty() ? "Unknown GPU" : Hardware.CARDS.getFirst().getName())
                    .integer("usage", "Minecraft GPU usage percent, or -1 when unavailable", gpuUsage));
            pc.namespace("memory", memory -> memory
                    .integer("used", "Memory used in MiB", () -> (int) ((runtime.totalMemory() - runtime.freeMemory()) / MIB))
                    .integer("allocated", "Memory allocated in MiB", () -> (int) (runtime.totalMemory() / MIB))
                    .integer("max", "Maximum memory in MiB", () -> (int) (runtime.maxMemory() / MIB))
                    .integer("free", "Memory still available in MiB", () -> (int) ((runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / MIB)));
            pc.namespace("display", display -> display
                    .integer("width", "Window width", () -> client.getWindow().getWidth())
                    .integer("height", "Window height", () -> client.getWindow().getHeight())
                    .integer("scale", "GUI scale", () -> (int) client.getWindow().getScaleFactor())
                    .integer("hz", "Monitor refresh rate", () -> client.getWindow().getRefreshRate()));
        });
    }

    private static int getGpuUsage(MinecraftClient client) {
        if (GlTimer.getInstance().isEmpty()) return -1;
        double usage = client.getGpuUtilizationPercentage();
        if (!Double.isFinite(usage) || usage < 0) return -1;
        return (int) Math.round(Math.min(100, usage));
    }
}
