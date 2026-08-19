package me.ifair.luahud.api.modules.pc;

import me.ifair.luahud.api.core.LazyTable;
import org.luaj.vm2.LuaTable;

public class MemoryProvider {
    private static final Runtime RUNTIME = Runtime.getRuntime();
    private static final long MIB = 1024 * 1024;

    public LuaTable build() {
        LazyTable memory = new LazyTable();

        // Did not use cache because reading memory is lightweight
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
