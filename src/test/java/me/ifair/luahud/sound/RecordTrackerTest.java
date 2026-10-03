package me.ifair.luahud.sound;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecordTrackerTest {
    private final RecordTracker tracker = new RecordTracker();
    private final BlockPos pos = BlockPos.ORIGIN;
    private final Vec3d listener = Vec3d.ofCenter(pos);

    @Test void playbackAdvancesInSecondsAndExpires() {
        assertNull(tracker.nearest(listener, ignored -> true));
        tracker.start(pos, "minecraft:music_disc_cat", "C418 - cat", 2.5);
        for (int i = 0; i < 25; i++) tracker.tick();
        var record = tracker.nearest(listener, ignored -> true);
        assertNotNull(record);
        assertEquals("minecraft:music_disc_cat", record.id());
        assertEquals("C418 - cat", record.name());
        assertEquals(2.5, record.length());
        assertEquals(1.25, record.elapsed());
        assertEquals(1.25, record.remaining());
        assertEquals(record, tracker.nearest(listener, ignored -> true));
        for (int i = 0; i < 25; i++) tracker.tick();
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
        tracker.tick();
        tracker.start(pos, "new", "New", 10);
        assertEquals(0, tracker.nearest(listener, ignored -> true).elapsed());
        tracker.stop(pos);
        assertNull(tracker.nearest(listener, ignored -> true));
        tracker.start(pos, "new", "New", 10);
        tracker.clear();
        assertNull(tracker.nearest(listener, ignored -> true));
    }
}
