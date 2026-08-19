package me.ifair.luahud.api.modules.pc;

import me.ifair.luahud.api.core.CachedValue;
import me.ifair.luahud.api.core.LazyTable;
import org.luaj.vm2.LuaTable;
import oshi.SystemInfo;
import oshi.hardware.GraphicsCard;

import java.util.List;
import java.util.function.IntSupplier;

public class GpuProvider {
    private final List<GraphicsCard> cards;
    private final CachedValue<Integer> usageCache;

    public GpuProvider(SystemInfo systemInfo) {
        this.cards = systemInfo.getHardware().getGraphicsCards();
        this.usageCache = new CachedValue<>(2000, this::fetchUsage);
    }

    public LuaTable build() {
        LazyTable gpu = new LazyTable();

        final String gpuName = !cards.isEmpty() ? cards.getFirst().getName() : "Unknown GPU";

        gpu.addString("name", () -> gpuName);
        gpu.addInt("usage", (IntSupplier) usageCache);
    }

    private int fetchUsage() {
        // TODO: Make this work
        return 3131;
    }
}
