package dev.ifair.luahud.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DamageTrackerTest {
    @Test void notificationAndHealthUpdateAreMatchedInEitherOrder() {
        for (boolean healthFirst : new boolean[]{true, false}) {
            DamageTracker tracker = initialized();
            if (healthFirst) tracker.onHealthUpdate(17);
            tracker.onDamage(notification("minecraft:arrow"));
            if (!healthFirst) tracker.onHealthUpdate(17);
            DamageEvent event = tracker.tick();
            assertNotNull(event);
            assertEquals("minecraft:arrow", event.type());
            assertEquals(3.0, event.amount());
            assertNull(tracker.tick());
        }
    }

    @Test void adjacentTicksAllowPacketsToArriveSeparately() {
        for (boolean healthFirst : new boolean[]{true, false}) {
            DamageTracker tracker = initialized();
            if (healthFirst) tracker.onHealthUpdate(17);
            else tracker.onDamage(notification("minecraft:fall"));
            assertNull(tracker.tick());
            if (healthFirst) tracker.onDamage(notification("minecraft:fall"));
            else tracker.onHealthUpdate(17);
            assertEquals(3.0, tracker.tick().amount());
        }
    }

    @Test void nearbyHitsCombineLossAndUseTheLatestSource() {
        DamageTracker tracker = initialized();
        tracker.onDamage(notification("minecraft:arrow"));
        tracker.onHealthUpdate(17);
        tracker.onDamage(notification("minecraft:fall"));
        tracker.onHealthUpdate(15);
        DamageEvent event = tracker.tick();
        assertEquals("minecraft:fall", event.type());
        assertEquals(5.0, event.amount());
        assertNull(tracker.tick());
    }

    @Test void repeatedHealthPacketsDoNotCountTheSameLossTwice() {
        DamageTracker tracker = initialized();
        tracker.onHealthUpdate(17);
        tracker.onHealthUpdate(17);
        tracker.onDamage(notification("minecraft:arrow"));
        assertEquals(3.0, tracker.tick().amount());
        tracker.onHealthUpdate(17);
        assertNull(tracker.tick());
    }

    @Test void unmatchedHealthLossUsesGenericInsteadOfAStaleAttacker() {
        DamageTracker tracker = initialized();
        tracker.onDamage(notification("minecraft:arrow"));
        tracker.onHealthUpdate(17);
        tracker.tick();
        tracker.onHealthUpdate(16);
        assertNull(tracker.tick());
        DamageEvent event = tracker.tick();
        assertEquals("minecraft:generic", event.type());
        assertEquals(1.0, event.amount());
        assertNull(event.attacker());
        assertNull(event.weapon());
    }

    @Test void notificationWithoutNormalHealthLossReportsZeroOnce() {
        DamageTracker tracker = initialized();
        tracker.onDamage(notification("minecraft:arrow"));
        assertNull(tracker.tick());
        assertEquals(0.0, tracker.tick().amount());
        assertNull(tracker.tick());
    }

    @Test void healingDoesNotGenerateDamageOrCancelAnObservedLoss() {
        DamageTracker tracker = initialized();
        tracker.onHealthUpdate(20);
        assertNull(tracker.tick());
        tracker.onHealthUpdate(15);
        tracker.onHealthUpdate(17);
        tracker.tick();
        assertEquals(5.0, tracker.tick().amount());
    }

    @Test void initialHealthAndRespawnDoNotGenerateDamage() {
        DamageTracker tracker = new DamageTracker();
        tracker.bind(new Object());
        tracker.onHealthUpdate(6);
        assertNull(tracker.tick());
        tracker.onDamage(notification("minecraft:fall"));
        tracker.onHealthUpdate(0);
        tracker.bind(new Object());
        tracker.onHealthUpdate(8);
        assertNull(tracker.tick());
        assertNull(tracker.tick());
        tracker.onHealthUpdate(5.5f);
        tracker.tick();
        assertEquals(2.5, tracker.tick().amount());
        tracker.onDamage(notification("minecraft:arrow"));
        tracker.bind(null);
        assertNull(tracker.tick());
    }

    private static DamageTracker initialized() {
        DamageTracker tracker = new DamageTracker();
        tracker.bind(new Object());
        tracker.onHealthUpdate(20);
        return tracker;
    }

    private static DamageEvent notification(String type) {
        return new DamageEvent(type, null, null, 0.0);
    }
}
