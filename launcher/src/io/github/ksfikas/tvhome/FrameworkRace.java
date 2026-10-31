package io.github.ksfikas.tvhome;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Android 14 can deliver a picture-in-picture state message to an activity that has just gone (the small
 * window closed while the voice assistant came up). The system then throws on our main thread and the whole
 * process dies, home screen included. That one error is harmless to drop, so the main loop swallows it and
 * carries on; any other error still crashes as usual.
 */
final class FrameworkRace {
    private FrameworkRace() { }

    private static final String MESSAGE = "Activity client record must not be null";

    /** True for the system's "activity already gone" error, however deeply it is wrapped. */
    static boolean is(Throwable t) {
        for (int depth = 0; t != null && depth < 8; t = t.getCause(), depth++) {
            if (t instanceof IllegalArgumentException && t.getMessage() != null && t.getMessage().contains(MESSAGE)) return true;
        }
        return false;
    }

    /** Runs the main loop inside a guard. Call once, early. */
    static void install() {
        new Handler(Looper.getMainLooper()).post(() -> {
            while (true) {
                try {
                    Looper.loop();
                } catch (RuntimeException e) {
                    if (!is(e)) throw e;
                    Log.w("tvh", "ignored a system race: " + e.getMessage());
                }
            }
        });
    }
}
