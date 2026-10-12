package dev.ifair.luahud.render;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class RenderQueueTest {
    private static final SectionLayout LAYOUT = SectionLayout.defaults(Anchor.TOP_LEFT, 2, 2);

    private static void draw(RenderQueue queue, String text) {
        queue.add(new RenderElement.Text(text, 0, 0, false, 0, 0), false);
    }

    @Test void onlyTheInvokedCallbackLosesItsUntimedSections() {
        RenderQueue queue = new RenderQueue();
        Object first = new Object(), second = new Object();
        queue.beginInvocation(first);
        draw(queue, "first");
        queue.endInvocation();
        queue.beginInvocation(second);
        draw(queue, "second");
        queue.endInvocation();

        queue.beginInvocation(first);
        queue.endInvocation();

        assertEquals(1, queue.sections().size());
        assertEquals("second", text(queue, 0));
    }

    @Test void timedSectionsAccumulateUntilTheirIndividualDeadlines() {
        AtomicLong clock = new AtomicLong();
        RenderQueue queue = new RenderQueue(clock::get);
        Object owner = new Object();
        queue.beginInvocation(owner);
        queue.beginSection(LAYOUT, null, 5.0);
        draw(queue, "first");
        queue.endInvocation();
        clock.set(2_000_000_000L);
        queue.beginInvocation(owner);
        queue.beginSection(LAYOUT, null, 5.0);
        draw(queue, "second");
        queue.endInvocation();
        assertEquals(2, queue.sections().size());

        clock.set(5_000_000_000L);
        assertTrue(queue.expire());
        assertEquals("second", text(queue, 0));
        clock.set(7_000_000_000L);
        assertTrue(queue.expire());
        assertTrue(queue.sections().isEmpty());
    }

    @Test void sharedIdsReplaceAcrossOwnersAndRestartTheDurationAtTheEndOfTheOrder() {
        AtomicLong clock = new AtomicLong();
        RenderQueue queue = new RenderQueue(clock::get);
        queue.beginInvocation(new Object());
        queue.beginSection(LAYOUT, "damageIndicator", 5.0);
        draw(queue, "old");
        queue.beginSection(LAYOUT);
        draw(queue, "unrelated");
        queue.endInvocation();
        clock.set(2_000_000_000L);
        queue.beginInvocation(new Object());
        queue.beginSection(LAYOUT, "damageIndicator", 5.0);
        draw(queue, "new");
        queue.endInvocation();
        assertEquals(2, queue.sections().size());
        assertEquals("unrelated", text(queue, 0));
        assertEquals("new", text(queue, 1));
        clock.set(5_000_000_000L);
        assertFalse(queue.expire());
        clock.set(7_000_000_000L);
        assertTrue(queue.expire());
        assertEquals("unrelated", text(queue, 0));
    }

    @Test void realTimeIgnoresTicksWhileGameTimeIgnoresWallTime() {
        AtomicLong clock = new AtomicLong();
        RenderQueue queue = new RenderQueue(clock::get);
        queue.beginSection(LAYOUT, null, 0.1);
        for (int i = 0; i < 100; i++) queue.tickClock();
        assertFalse(queue.expire());
        clock.set(100_000_000L);
        assertTrue(queue.expire());

        queue.configureClock(true);
        queue.beginSection(LAYOUT, null, 0.1);
        clock.set(100_000_000_000L);
        assertFalse(queue.expire());
        queue.tickClock();
        assertFalse(queue.expire());
        queue.tickClock();
        assertTrue(queue.expire());
    }

    @Test void invalidOptionsDoNotRemoveTheExistingId() {
        RenderQueue queue = new RenderQueue();
        queue.beginSection(LAYOUT, "kept", null);
        draw(queue, "kept");
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> queue.beginSection(LAYOUT, "kept", invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> queue.beginSection(LAYOUT, " ", 1.0));
        assertEquals("kept", text(queue, 0));
    }

    @Test void emptyInvocationsDoNotAllocateSectionsAndClearDropsAllEntries() {
        RenderQueue queue = new RenderQueue();
        for (int i = 0; i < 100; i++) {
            queue.beginInvocation(new Object());
            queue.endInvocation();
        }
        assertTrue(queue.sections().isEmpty());
        queue.beginSection(LAYOUT, "timed", 1.0);
        queue.clear();
        assertTrue(queue.sections().isEmpty());
    }

    @Test void nestedInvocationsRestoreTheOuterSection() {
        RenderQueue queue = new RenderQueue();
        queue.beginInvocation(new Object());
        draw(queue, "outer");
        queue.beginInvocation(new Object());
        draw(queue, "inner");
        queue.endInvocation();
        draw(queue, "outer again");
        queue.endInvocation();
        assertEquals(2, queue.sections().size());
        assertEquals(2, queue.sections().getFirst().rows().getFirst().elements().size());
    }

    private static String text(RenderQueue queue, int index) {
        return ((RenderElement.Text) queue.sections().get(index).rows().getFirst().elements().getFirst()).value();
    }
}
