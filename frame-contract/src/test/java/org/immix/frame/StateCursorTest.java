package org.immix.frame;

import org.junit.Test;
import static org.junit.Assert.*;

public class StateCursorTest {
    @Test public void ignoresCallbacksUntilProviderIsKnown() {
        assertFalse(new StateCursor().accept("old", 1));
    }
    @Test public void ignoresQueuedStateOlderThanSnapshot() {
        StateCursor c = new StateCursor(); c.reset("provider");
        assertTrue(c.accept("provider", 5));
        assertFalse(c.accept("provider", 4));
        assertFalse(c.accept("provider", 5));
        assertTrue(c.accept("provider", 6));
    }
    @Test public void staleCallbacksFromDeadProviderCannotOverrideRestart() {
        StateCursor c = new StateCursor(); c.reset("old"); c.accept("old", 100);
        c.reset("new");
        assertTrue(c.accept("new", 0));
        assertFalse(c.accept("old", 101));
        assertTrue(c.accept("new", 1));
    }
    @Test public void disconnectRejectsPreviouslyQueuedCallbacks() {
        StateCursor c = new StateCursor(); c.reset("old"); c.reset(null);
        assertFalse(c.accept("old", 1));
    }
}
