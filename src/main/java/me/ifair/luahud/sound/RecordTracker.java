package me.ifair.luahud.sound;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Tracks client playback ticks, so pausing also pauses the exposed timer. */
public final class RecordTracker {
    public record PlayingRecord(String id, String name, double length, double elapsed) {
        public double remaining() { return Math.max(0, length - elapsed); }
    }

    private record Playback(String id, String name, double length, long started) {}
    private final Map<BlockPos, Playback> records = new HashMap<>();
    private long ticks;

    public void start(BlockPos pos, String id, String name, double length) {
        records.put(pos.toImmutable(), new Playback(id, name, length, ticks));
    }

    public void stop(BlockPos pos) { records.remove(pos); }

    public void clear() {
        records.clear();
        ticks = 0;
    }

    public void tick() {
        ticks++;
        records.values().removeIf(record -> (ticks - record.started()) / 20.0 >= record.length());
    }

    public PlayingRecord nearest(Vec3d listener, Predicate<BlockPos> playing) {
        Playback nearest = null;
        double distance = 64 * 64;
        for (var entry : records.entrySet()) {
            double candidateDistance = Vec3d.ofCenter(entry.getKey()).squaredDistanceTo(listener);
            if (candidateDistance <= distance && playing.test(entry.getKey())) {
                distance = candidateDistance;
                nearest = entry.getValue();
            }
        }
        return nearest == null ? null : new PlayingRecord(nearest.id(), nearest.name(), nearest.length(),
                Math.clamp((ticks - nearest.started()) / 20.0, 0.0, nearest.length()));
    }
}
