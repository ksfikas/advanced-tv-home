package io.github.ksfikas.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Only the system's "activity already gone" error is let through. */
public class FrameworkRaceTest {

    @Test
    public void theActivityGoneErrorIsRecognised() {
        assertTrue(FrameworkRace.is(new IllegalArgumentException(
                "Activity client record must not be null to execute transaction item: PipStateTransactionItem{}")));
        assertTrue(FrameworkRace.is(new RuntimeException("wrapped",
                new IllegalArgumentException("Activity client record must not be null to execute transaction item: X"))));
    }

    @Test
    public void everythingElseStillCrashes() {
        assertFalse(FrameworkRace.is(new IllegalArgumentException("something else")));
        assertFalse(FrameworkRace.is(new IllegalStateException("Activity client record must not be null")));
        assertFalse(FrameworkRace.is(new NullPointerException()));
        assertFalse(FrameworkRace.is(null));
    }
}
