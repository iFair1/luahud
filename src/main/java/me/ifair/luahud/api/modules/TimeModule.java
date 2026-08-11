package me.ifair.luahud.api.modules;

import me.ifair.luahud.api.core.LazyTable;
import me.ifair.luahud.api.core.ApiModule;
import net.minecraft.client.MinecraftClient;
import org.luaj.vm2.LuaTable;

public class TimeModule implements ApiModule {

    private static final long TICKS_PER_DAY = 24000L;
    private static final long TICKS_PER_HOUR = 1000L;
    private static final long TIME_OFFSET = 6000L; // TimeTick 0 is 06:00 in the game

    @Override
    public void register(LuaTable lh) {
        LazyTable time = new LazyTable();

        time.addDouble("tick", this::getTimeInDay);
        time.addInt("day", this::getDay);
        time.addInt("hour", this::getHour);
        time.addInt("minute", this::getMinute);
        time.addInt("second", this::getSecond);

        lh.set("time", time.build());
    }

    private long getTimeOfDay() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world != null ? client.world.getTimeOfDay() : 0;
    }

    private long getTimeInDay() {
        return getTimeOfDay() % TICKS_PER_DAY;
    }

    private int getDay() {
        return (int) (getTimeOfDay() / TICKS_PER_DAY);
    }

    private long getShiftedTime() {
        return (getTimeOfDay() + TIME_OFFSET) % TICKS_PER_DAY;
    }

    private int getHour() {
        return (int) (getShiftedTime() / TICKS_PER_HOUR);
    }

    private int getMinute() {
        long ticksInCurrentHour = getShiftedTime() % TICKS_PER_HOUR;
        return (int) (ticksInCurrentHour * 60 / TICKS_PER_HOUR);
    }

    private int getSecond() {
        long ticksInCurrentHour = getShiftedTime() % TICKS_PER_HOUR;
        return (int) ((ticksInCurrentHour * 3600 / TICKS_PER_HOUR) % 60);
    }
}
