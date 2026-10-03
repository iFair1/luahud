package me.ifair.luahud.sound;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Tracks real playback time independently of game ticks, excluding sound pauses. */
public final class RecordTracker {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    public record PlayingRecord(String id, String name, double length, int elapsed) {
        public int remaining() { return (int) Math.ceil(Math.max(0, length - elapsed)); }
    }

    private record Playback(String id, String name, double length, long started) {
        double elapsedSeconds(long playbackTime) {
            return (double) (playbackTime - started) / NANOS_PER_SECOND;
        }
    }
    private final Map<BlockPos, Playback> records = new HashMap<>();
    private final LongSupplier nanoTime;
    private long lastTime;
    private long playbackTime;
    private boolean paused;

    public RecordTracker() {
        this(System::nanoTime);
    }

    RecordTracker(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
        lastTime = nanoTime.getAsLong();
    }

    public void start(BlockPos pos, String id, String name, double length) {
        updateTime();
        records.put(pos.toImmutable(), new Playback(id, name, length, playbackTime));
    }

    public void stop(BlockPos pos) { records.remove(pos); }

    public void clear() {
        records.clear();
        playbackTime = 0;
        lastTime = nanoTime.getAsLong();
    }

    public void setPaused(boolean paused) {
        updateTime();
        this.paused = paused;
    }

    private void updateTime() {
        long now = nanoTime.getAsLong();
        if (!paused) playbackTime += now - lastTime;
        lastTime = now;
    }

    public PlayingRecord nearest(Vec3d listener, Predicate<BlockPos> playing) {
        updateTime();
        records.values().removeIf(record -> record.elapsedSeconds(playbackTime) >= record.length());
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
                (int) nearest.elapsedSeconds(playbackTime));
    }
}
