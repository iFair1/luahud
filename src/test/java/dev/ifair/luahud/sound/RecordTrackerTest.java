package dev.ifair.luahud.sound;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class RecordTrackerTest {
    private final AtomicLong nanoTime = new AtomicLong();
    private final RecordTracker tracker = new RecordTracker(nanoTime::get);
    private final BlockPos pos = BlockPos.ORIGIN;
    private final Vec3d listener = Vec3d.ofCenter(pos);

    @Test void playbackAdvancesInSecondsAndExpires() {
        assertNull(tracker.nearest(listener, ignored -> true));
        tracker.start(pos, "minecraft:music_disc_cat", "C418 - cat", 2.5);
        nanoTime.addAndGet(1_250_000_000L);
        var record = tracker.nearest(listener, ignored -> true);
        assertNotNull(record);
        assertEquals("minecraft:music_disc_cat", record.id());
        assertEquals("C418 - cat", record.name());
        assertEquals(2.5, record.length());
        assertEquals(1, record.elapsed());
        assertEquals(2, record.remaining());
        assertEquals(record, tracker.nearest(listener, ignored -> true));
        nanoTime.addAndGet(1_249_999_999L);
        record = tracker.nearest(listener, ignored -> true);
        assertNotNull(record);
        assertEquals(2, record.elapsed());
        assertEquals(1, record.remaining());
        nanoTime.incrementAndGet();
        assertNull(tracker.nearest(listener, ignored -> true));
    }

    @Test void timersFollowRealSecondsWithoutGameTicks() {
        tracker.start(pos, "disc", "Disc", 3);
        nanoTime.set(999_999_999L);
        assertEquals(0, tracker.nearest(listener, ignored -> true).elapsed());
        assertEquals(3, tracker.nearest(listener, ignored -> true).remaining());
        nanoTime.set(1_000_000_000L);
        assertEquals(1, tracker.nearest(listener, ignored -> true).elapsed());
        assertEquals(2, tracker.nearest(listener, ignored -> true).remaining());
        nanoTime.set(2_000_000_000L);
        assertEquals(2, tracker.nearest(listener, ignored -> true).elapsed());
        assertEquals(1, tracker.nearest(listener, ignored -> true).remaining());
        nanoTime.set(3_000_000_000L);
        assertNull(tracker.nearest(listener, ignored -> true));
    }

    @Test void infrequentReadsDoNotSlowPlaybackTime() {
        tracker.start(pos, "disc", "Disc", 100);
        nanoTime.addAndGet(10_500_000_000L);
        var record = tracker.nearest(listener, ignored -> true);
        assertEquals(10, record.elapsed());
        assertEquals(90, record.remaining());
        assertEquals(record, tracker.nearest(listener, ignored -> true));
    }

    @Test void soundPausesExcludeRealTimeAndPreservePartialSeconds() {
        tracker.start(pos, "disc", "Disc", 3);
        nanoTime.addAndGet(750_000_000L);
        tracker.setPaused(true);
        nanoTime.addAndGet(30_000_000_000L);
        assertEquals(0, tracker.nearest(listener, ignored -> true).elapsed());
        assertEquals(3, tracker.nearest(listener, ignored -> true).remaining());
        tracker.setPaused(true);
        tracker.setPaused(false);
        nanoTime.addAndGet(250_000_000L);
        assertEquals(1, tracker.nearest(listener, ignored -> true).elapsed());
        assertEquals(2, tracker.nearest(listener, ignored -> true).remaining());
        tracker.setPaused(false);
        nanoTime.addAndGet(2_000_000_000L);
        assertNull(tracker.nearest(listener, ignored -> true));
    }

    @Test void selectsNearestActiveDiscWithinRange() {
        tracker.start(pos, "near", "Near", 100);
        BlockPos farther = new BlockPos(10, 0, 0);
        tracker.start(farther, "far", "Far", 100);
        assertEquals("near", tracker.nearest(listener, ignored -> true).id());
        assertEquals("far", tracker.nearest(listener, farther::equals).id());
        assertNull(tracker.nearest(listener, ignored -> false));
        assertNull(tracker.nearest(listener.add(100, 0, 0), ignored -> true));
    }

    @Test void stoppingRestartingAndWorldChangesDiscardOldPlayback() {
        tracker.start(pos, "old", "Old", 10);
        nanoTime.addAndGet(5_000_000_000L);
        tracker.start(pos, "new", "New", 10);
        assertEquals(0, tracker.nearest(listener, ignored -> true).elapsed());
        tracker.stop(pos);
        assertNull(tracker.nearest(listener, ignored -> true));
        tracker.start(pos, "new", "New", 10);
        tracker.clear();
        assertNull(tracker.nearest(listener, ignored -> true));
    }
}
