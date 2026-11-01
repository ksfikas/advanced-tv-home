package io.github.ksfikas.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What Tvheadend tells us without being asked: its web interface keeps one request open ("comet") and the
 * server answers when something changes. Recordings starting, stopping or being cancelled, a programme's
 * recording marker, and the tuner's signal arrive that way. It only runs while a screen that wants it is
 * showing, and gives up quietly when the server cannot be reached.
 */
final class ServerEvents {
    private ServerEvents() { }

    /** A recording or the guide changed: refresh what is on screen. Called on the main thread, bursts merged. */
    interface Listener { void onChange(); }

    /** The tuner in use, as last reported. */
    static final class Signal {
        final long at = System.currentTimeMillis();
        String input;
        int signal, signalScale, snr, snrScale, ber, unc, cc;
        long bps;

        Tvh.Status toStatus() {
            Tvh.Status s = new Tvh.Status();
            s.tuner = input == null ? null : input.replaceAll("\\s*#\\d+", "").replace(" : ", " · ");
            s.signal = Tvh.level(signal, signalScale, "dBm");
            s.snr = Tvh.level(snr, snrScale, "dB");
            s.errors = "BER " + ber + " · UNC " + unc + " · CC " + cc;
            s.bitrate = bps;
            return s;
        }
    }

    /** What one reply holds: whether recordings or the guide changed, and the newest tuner report if any. */
    static final class Result {
        boolean changed;
        Signal signal;
    }

    static Result parse(String body) throws JSONException {
        Result r = new Result();
        JSONArray msgs = new JSONObject(body).optJSONArray("messages");
        if (msgs == null) return r;
        for (int i = 0; i < msgs.length(); i++) {
            JSONObject m = msgs.optJSONObject(i);
            if (m == null) continue;
            String cls = m.optString("notificationClass");
            if (cls.equals("dvrentry")) {
                r.changed = true;
            } else if (cls.equals("epg") && (m.has("dvr_update") || m.has("update") || m.has("create") || m.has("delete"))) {
                r.changed = true;
            } else if (cls.equals("input_status") && m.has("snr") && m.optInt("subs") > 0) {
                Signal s = new Signal();
                s.input = m.optString("input");
                s.signal = m.optInt("signal");
                s.signalScale = m.optInt("signal_scale");
                s.snr = m.optInt("snr");
                s.snrScale = m.optInt("snr_scale");
                s.ber = m.optInt("ber");
                s.unc = m.optInt("unc");
                s.cc = m.optInt("cc");
                s.bps = m.optLong("bps");
                r.signal = s;
            }
        }
        return r;
    }

    private static final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private static Thread thread;
    private static volatile boolean running;
    private static volatile Signal latest;
    private static Handler uiHandler;
    private static boolean pending;

    private static Handler ui() {
        if (uiHandler == null) uiHandler = new Handler(Looper.getMainLooper());
        return uiHandler;
    }

    /** Starts listening (if not already) and calls this listener on changes, until {@link #unwatch}. */
    static synchronized void watch(Context c, Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
        running = true;
        if (thread != null && thread.isAlive()) return;
        Context app = c.getApplicationContext();
        thread = new Thread(() -> loop(app), "ServerEvents");
        thread.setDaemon(true);
        thread.start();
    }

    /** The last listener leaving stops the connection (within about ten seconds). */
    static synchronized void unwatch(Listener l) {
        listeners.remove(l);
        if (listeners.isEmpty()) running = false;
    }

    /** The tuner report from the last few seconds, or null (then ask the server). */
    static Tvh.Status status() {
        Signal s = latest;
        return s != null && System.currentTimeMillis() - s.at < 4000 ? s.toStatus() : null;
    }

    private static void fire() {
        synchronized (ServerEvents.class) {
            if (pending) return;
            pending = true;
        }
        ui().postDelayed(() -> {
            synchronized (ServerEvents.class) { pending = false; }
            for (Listener l : listeners) l.onChange();
        }, 600);
    }

    private static void loop(Context app) {
        long wait = 5000;
        String box = null;
        while (running) {
            Tvh tvh = Tvh.get(app);
            if (tvh == null) return;
            try {
                String path = box == null ? "/comet/poll?boxid=&immediate=1" : "/comet/poll?boxid=" + box;
                String body = new String(tvh.getLong(path, 30_000), StandardCharsets.UTF_8);
                box = new JSONObject(body).optString("boxid", box);
                Result r = parse(body);
                if (r.signal != null) latest = r.signal;
                if (r.changed) fire();
                wait = 5000;
            } catch (IOException | JSONException e) {
                box = null; // a restarted server has forgotten us
                try { Thread.sleep(wait); } catch (InterruptedException ie) { return; }
                wait = Math.min(wait * 2, 120_000);
            }
        }
    }
}
