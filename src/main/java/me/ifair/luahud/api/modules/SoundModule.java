package me.ifair.luahud.api.modules;

import me.ifair.luahud.api.LuaApiRegistrar;
import me.ifair.luahud.api.LuaDoc;
import me.ifair.luahud.api.LuaType;
import me.ifair.luahud.sound.RecordTracker;
import me.ifair.luahud.sound.SoundState;
import net.minecraft.client.MinecraftClient;

public final class SoundModule {
    private SoundModule() {}

    public static void register(LuaApiRegistrar api, MinecraftClient client) {
        var record = api.model("PlayingRecord", RecordTracker.PlayingRecord.class);
        record.string("id", "Namespaced disc item ID (song ID when no matching item exists)", RecordTracker.PlayingRecord::id);
        record.string("name", "Localized song name", RecordTracker.PlayingRecord::name);
        record.number("length", "Song length in seconds", RecordTracker.PlayingRecord::length);
        record.integer("elapsed", "Elapsed playback time in whole seconds", RecordTracker.PlayingRecord::elapsed);
        record.integer("remaining", "Remaining playback time in seconds (rounded up for fractional song lengths)", RecordTracker.PlayingRecord::remaining);
        api.root().namespace("sound", sound -> sound
                .number("mood", "Ambient mood percentage (0 to 100), as shown in F3",
                        () -> client.player == null ? 0.0 : client.player.getMoodPercentage() * 100.0)
                .value("record", LuaType.optional(record.type()),
                        LuaDoc.of("Nearest playing jukebox disc within 64 blocks, or nil"),
                        () -> client.world == null || client.player == null ? null
                                : ((SoundState) client.world).luahud$getRecord()));
    }
}
