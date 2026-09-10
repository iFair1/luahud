package me.ifair.luahud.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    }
}
