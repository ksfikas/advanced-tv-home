package io.github.ksfikas.tvhome;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Incoming calls from a Fritz!Box, shown as a banner. The router offers a call monitor on port 1012 of the
 * home network: it sends one text line per event, e.g. {@code 31.10.26 21:05:11;RING;0;0301234567;12345;SIP0;}.
 * It has to be switched on once by dialling #96*5* on a phone connected to the router. Off by default.
 */
final class CallMonitor {
    private CallMonitor() { }

    static final int PORT = 1012;
    private static final String ENABLED = "fritz_calls";

    /** An incoming call: the router's id for it, the caller's number (empty when hidden) and the line called. */
    static final class Ring {
        final String id, number, called;
        Ring(String id, String number, String called) { this.id = id; this.number = number; this.called = called; }
    }

    /** The incoming call a monitor line announces, or null for anything else (calls out, connects, hang-ups). */
    static Ring parse(String line) {
        if (line == null) return null;
        String[] f = line.trim().split(";", -1);
        if (f.length < 5 || !f[1].equals("RING")) return null;
        return new Ring(f[2], f[3].trim(), f[4].trim());
    }

    private static volatile Thread worker;
    private static volatile Socket open;
    private static volatile boolean wanted;

    static boolean enabled(Context c) { return Tvh.prefs(c).getBoolean(ENABLED, false); }

    static void setEnabled(Context c, boolean on) {
        Tvh.prefs(c).edit().putBoolean(ENABLED, on).apply();
        if (on) start(c); else stop();
    }

    /** Starts listening if switched on and not already; harmless to call again. */
    static synchronized void start(Context c) {
        if (!enabled(c) || worker != null) return;
        Context app = c.getApplicationContext();
        Notice.hook(app);
        wanted = true;
        Thread t = new Thread(() -> listen(app), "CallMonitor");
        t.setDaemon(true);
        worker = t;
        t.start();
    }

    static synchronized void stop() {
        wanted = false;
        Socket s = open;
        if (s != null) { try { s.close(); } catch (IOException ignored) { } }
        worker = null;
    }

    private static void listen(Context c) {
        long wait = 30_000;
        String lastId = null;
        while (wanted) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(Fritz.host(c), PORT), 5000);
                s.setKeepAlive(true);
                open = s;
                wait = 30_000;
                Tvh.IO.execute(() -> Fritz.nameFor(c, "00000")); // warm up the phonebook so the first call shows a name at once
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while (wanted && (line = in.readLine()) != null) {
                    Ring r = parse(line);
                    if (r == null || r.id.equals(lastId)) continue;
                    lastId = r.id;
                    String who = r.number.isEmpty() ? c.getString(R.string.calls_unknown) : r.number;
                    String name = r.number.isEmpty() ? null : Fritz.nameFor(c, r.number);
                    Notice.show(c, c.getString(R.string.calls_title), name == null ? who : name + " · " + who, 20);
                }
            } catch (IOException ignored) {
                // router off, call monitor not switched on, or the connection dropped: try again later
            } finally {
                open = null;
            }
            if (!wanted) break;
            try { Thread.sleep(wait); } catch (InterruptedException e) { return; }
            wait = Math.min(wait * 2, 5 * 60_000);
        }
        synchronized (CallMonitor.class) { if (!wanted) worker = null; }
    }
}
