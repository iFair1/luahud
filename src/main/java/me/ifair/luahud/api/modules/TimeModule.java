package me.ifair.luahud.api.modules;

import me.ifair.luahud.api.LuaApiRegistrar;
import net.minecraft.client.MinecraftClient;

public final class TimeModule {
    private static final long TICKS_PER_DAY = 24000L;
    private static final long TICKS_PER_HOUR = 1000L;
    private static final long TIME_OFFSET = 6000L;

    private TimeModule() {}

    public static void register(LuaApiRegistrar api, MinecraftClient client) {
        api.root().namespace("time", time -> time
                .number("tick", "Tick within the current Minecraft day", () -> (double) (worldTime(client) % TICKS_PER_DAY))
                .integer("day", "Days elapsed in the world", () -> (int) (worldTime(client) / TICKS_PER_DAY))
                .integer("hour", "Minecraft hour", () -> (int) (shiftedTime(client) / TICKS_PER_HOUR))
                .integer("minute", "Minecraft minute", () -> (int) ((shiftedTime(client) % TICKS_PER_HOUR) * 60 / TICKS_PER_HOUR))
                .integer("second", "Minecraft second", () -> (int) (((shiftedTime(client) % TICKS_PER_HOUR) * 3600 / TICKS_PER_HOUR) % 60)));
    }

    private static long worldTime(MinecraftClient client) {
        return client.world == null ? 0 : client.world.getTimeOfDay();
    }

    private static long shiftedTime(MinecraftClient client) {
        return (worldTime(client) + TIME_OFFSET) % TICKS_PER_DAY;
    }
}
