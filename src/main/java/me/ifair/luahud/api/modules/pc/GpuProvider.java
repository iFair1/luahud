package me.ifair.luahud.api.modules.pc;

import me.ifair.luahud.LuaHUD;
import me.ifair.luahud.api.core.CachedValue;
import me.ifair.luahud.api.core.LazyTable;
import org.luaj.vm2.LuaTable;
import oshi.PlatformEnum;
import oshi.SystemInfo;
import oshi.hardware.GraphicsCard;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class GpuProvider {
    private final List<GraphicsCard> cards;
    private final CachedValue<Integer> usageCache;
    private final PlatformEnum currentPlatform;

    public GpuProvider(SystemInfo systemInfo) {
        this.cards = systemInfo.getHardware().getGraphicsCards();
        this.currentPlatform = SystemInfo.getCurrentPlatform();
        this.usageCache = new CachedValue<>(2000, this::fetchUsage);
    }

    public LuaTable build() {
        LazyTable gpu = new LazyTable();

        final String gpuName = !cards.isEmpty() ? cards.getFirst().getName() : "Unknown GPU";

        gpu.addString("name", () -> gpuName);
        gpu.addInt("usage", usageCache::get);

        return gpu.build();
    }

    private int fetchUsage() {
        try {
            if (currentPlatform == PlatformEnum.LINUX) {
                return getLinuxUsage();
            } else if (currentPlatform == PlatformEnum.WINDOWS) {
                return getWindowsUsage();
            }
        } catch (Exception e) {
            LuaHUD.LOGGER.error("Error while getting GPU Usage");
            return -1;
        }
        return -1;
    }

    private int getNvidiaSmiUsage() {
        try {
            ProcessBuilder pb = new ProcessBuilder("nvidia-smi", "--query-gpu=utilization.gpu", "--format=csv,noheader,nounits");
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    return Integer.parseInt(line.trim());
                }
            }
        } catch (Exception ignored) {} // If there is no nvidia-smi
        return -1;
    }

    private int getLinuxUsage() {
        // nvidia-smi first because if the user has an integrated gpu (which is probably amd or intel) prioritizes it. I assume if user has an nvidia gpu, it's their primary one.
        int nvidiaUsage = getNvidiaSmiUsage();
        if (nvidiaUsage != -1) return nvidiaUsage;

        // AMD or Intel (sysfs)
        File usageFile = new File("/sys/class/drm/card0/device/gpu_busy_percent");
        if (usageFile.exists()) {
            try {
                String content = new String(Files.readAllBytes(Paths.get(usageFile.getAbsolutePath()))).trim();
                return Integer.parseInt(content);
            } catch (Exception ignored) {}
        }

        return -1;
    }

    private int getWindowsUsage() {
        int nvidiaUsage = getNvidiaSmiUsage();
        if (nvidiaUsage != -1) return nvidiaUsage;

        try {
            String cmd = "powershell -Command \"(Get-Counter '\\GPU Engine(*)\\Utilization Percentage').CounterSamples | Measure-Object -Property CookedValue -Sum | Select-Object -ExpandProperty Sum\"";
            Process process = Runtime.getRuntime().exec(cmd);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    double value = Double.parseDouble(line.trim().replace(",", "."));
                    return (int) Math.min(100, Math.round(value));
                }
            }
        } catch (Exception ignored) {}

        return -1;
    }
}
