package dev.ifair.luahud.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorManagerTest {
    @BeforeEach
    void reset() {
        ErrorManager.reset();
    }

    @Test
    void duplicateErrorsIncrementWithoutRepeatingNotifications() {
        ErrorManager.reportProfile("Example", "tick", "target is nil");
        ErrorManager.reportProfile("Example", "tick", "target is nil");

        List<ErrorEntry> errors = ErrorManager.snapshot();
        assertEquals(1, errors.size());
        assertEquals(2, errors.getFirst().count());
        assertEquals(1, ErrorManager.drainNotifications().size());
        assertTrue(ErrorManager.drainNotifications().isEmpty());
    }

    @Test
    void everyOccurrenceHasAnIndependentSnapshotAndStableId() {
        ErrorManager.reportProfile("Example", "tick", "target is nil");
        ErrorManager.reportProfile("Example", "tick", "target is nil");
        List<ErrorEntry> events = ErrorManager.drainEvents();

        assertEquals(2, events.size());
        assertEquals(events.getFirst().id(), events.getLast().id());
        assertEquals(1, events.getFirst().count());
        assertTrue(events.getFirst().isNew());
        assertEquals(2, events.getLast().count());
        assertFalse(events.getLast().isNew());
        assertTrue(ErrorManager.drainEvents().isEmpty());
        assertEquals(1, ErrorManager.drainNotifications().size());
    }

    @Test
    void suppressedEventsStillRecordErrorsAndRestoreDeliveryAfterFailure() {
        assertThrows(IllegalStateException.class, () -> ErrorManager.withoutEvents(() -> {
            ErrorManager.withoutEvents(() -> ErrorManager.reportProfile("Example", "error", "handler failed"));
            ErrorManager.reportSystem("nested", "nested failure");
            throw new IllegalStateException("test");
        }));
        assertTrue(ErrorManager.drainEvents().isEmpty());
        assertEquals(2, ErrorManager.distinctCount());
        assertEquals(2, ErrorManager.drainNotifications().size());

        ErrorManager.reportProfile("Example", "tick", "normal failure");
        assertEquals(1, ErrorManager.drainEvents().size());
    }

    @Test
    void reloadDiscardsOldProfileEventsAndKeepsSystemEvents() {
        ErrorManager.reportProfile("Example", "tick", "old failure");
        ErrorManager.reportSystem("config", "bad JSON");
        ErrorManager.beginProfileReload();

        List<ErrorEntry> events = ErrorManager.drainEvents();
        assertEquals(1, events.size());
        assertTrue(events.getFirst().system());
    }

    @Test
    void dismissRemovesAllOccurrencesAndPendingNotification() {
        for (int i = 0; i < 10; i++) {
            ErrorManager.reportProfile("Example", "tick", "target is nil");
        }
        ErrorEntry error = ErrorManager.snapshot().getFirst();
        assertEquals(10, error.count());
        long revision = ErrorManager.structureRevision();

        assertTrue(ErrorManager.dismiss(error.id()));

        assertTrue(ErrorManager.snapshot().isEmpty());
        assertTrue(ErrorManager.find(error.id()).isEmpty());
        assertEquals(0, ErrorManager.distinctCount());
        assertTrue(ErrorManager.drainNotifications().isEmpty());
        assertEquals(revision + 1, ErrorManager.structureRevision());
        assertFalse(ErrorManager.dismiss(error.id()));
        assertEquals(revision + 1, ErrorManager.structureRevision());
    }

    @Test
    void dismissedErrorCanReturnAsANewErrorWithANotification() {
        ErrorManager.reportProfile("Example", "tick", "target is nil");
        ErrorEntry original = ErrorManager.drainNotifications().getFirst();
        ErrorManager.dismiss(original.id());

        ErrorManager.reportProfile("Example", "tick", "target is nil");

        ErrorEntry reportedAgain = ErrorManager.snapshot().getFirst();
        assertTrue(reportedAgain.id() > original.id());
        assertEquals(1, reportedAgain.count());
        assertEquals(List.of(reportedAgain), ErrorManager.drainNotifications());
        assertTrue(reportedAgain.isNew());
    }

    @Test
    void dismissSystemErrorKeepsOtherErrorsAndAllowsItToReturn() {
        ErrorManager.reportSystem("config read", "bad JSON");
        ErrorManager.reportProfile("Example", "load", "bad Lua");
        ErrorEntry system = ErrorManager.snapshot().getFirst();
        ErrorEntry profile = ErrorManager.snapshot().getLast();

        assertTrue(ErrorManager.dismiss(system.id()));

        assertEquals(List.of(profile), ErrorManager.snapshot());
        assertEquals(List.of(profile), ErrorManager.drainNotifications());
        ErrorManager.reportSystem("config read", "bad JSON");
        ErrorEntry reportedAgain = ErrorManager.drainNotifications().getFirst();
        assertTrue(reportedAgain.system());
        assertEquals(1, reportedAgain.count());
        assertTrue(reportedAgain.id() > system.id());
    }

    @Test
    void dismissFreesSpaceInACappedGroup() {
        for (int i = 0; i < ErrorManager.MAX_DISTINCT_PER_GROUP; i++) {
            ErrorManager.reportProfile("Example", "tick", "error " + i);
        }
        ErrorManager.dismiss(ErrorManager.snapshot().getFirst().id());

        ErrorManager.reportProfile("Example", "tick", "new error");

        assertEquals(ErrorManager.MAX_DISTINCT_PER_GROUP, ErrorManager.distinctCount());
        assertEquals("new error", ErrorManager.snapshot().getLast().message());
        assertTrue(ErrorManager.snapshot().stream().noneMatch(error -> error.phase().equals("limit")));
    }

    @Test
    void profileReloadClearsProfilesButRetainsSystemErrors() {
        ErrorManager.reportProfile("Example", "load", "bad Lua");
        ErrorManager.reportSystem("config read", "bad JSON");

        ErrorManager.beginProfileReload();

        List<ErrorEntry> errors = ErrorManager.snapshot();
        assertEquals(1, errors.size());
        assertTrue(errors.getFirst().system());
        assertEquals(ErrorManager.SYSTEM_GROUP, errors.getFirst().group());
        assertEquals(1, ErrorManager.drainNotifications().size());
    }

    @Test
    void distinctErrorsAreCappedPerGroup() {
        for (int i = 0; i < ErrorManager.MAX_DISTINCT_PER_GROUP + 2; i++) {
            ErrorManager.reportProfile("Example", "tick", "error " + i);
        }

        List<ErrorEntry> errors = ErrorManager.snapshot();
        assertEquals(ErrorManager.MAX_DISTINCT_PER_GROUP + 1, errors.size());
        ErrorEntry overflow = errors.getLast();
        assertEquals("limit", overflow.phase());
        assertEquals(2, overflow.count());
        assertFalse(overflow.system());
        List<ErrorEntry> events = ErrorManager.drainEvents();
        assertEquals(ErrorManager.MAX_DISTINCT_PER_GROUP + 2, events.size());
        assertEquals(overflow.id(), events.get(events.size() - 2).id());
        assertTrue(events.get(events.size() - 2).isNew());
        assertFalse(events.getLast().isNew());
    }

    @Test
    void throwableTracebackIsRemovedButMultilineMessageIsPreserved() {
        ErrorManager.reportProfile("Example", "tick",
                new IllegalStateException("Example.lua:12: target is nil\nstack traceback:\n\tExample.lua:12"));

        ErrorEntry error = ErrorManager.snapshot().getFirst();
        assertEquals("12", error.line());
        assertEquals("Example.lua", error.source());
        assertEquals("target is nil", error.displayMessage());

        ErrorManager.reportSystem("config read", "first line\r\nsecond line");
        ErrorEntry system = ErrorManager.snapshot().stream().filter(ErrorEntry::system).findFirst().orElseThrow();
        assertEquals("first line\nsecond line", system.displayMessage());
        assertEquals("first line second line", system.summary());
    }
}
