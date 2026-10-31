package io.github.ksfikas.tvhome;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;

/**
 * A short message from outside (a doorbell, a script, Home Assistant) shown over whatever this app has on
 * screen, live TV included. Over another app (Netflix, say) it falls back to a plain toast, because Android
 * only lets an app draw over others with a special permission this app does not ask for.
 */
final class Notice {
    private Notice() { }

    static final int MIN_SECONDS = 3, MAX_SECONDS = 30, DEFAULT_SECONDS = 8;
    private static final int MAX_TITLE = 80, MAX_TEXT = 240;

    private static Handler uiHandler;

    /** Created on first use so the plain-JVM tests can load this class. */
    private static Handler ui() {
        if (uiHandler == null) uiHandler = new Handler(Looper.getMainLooper());
        return uiHandler;
    }
    private static WeakReference<Activity> top;
    private static View shown;
    private static boolean hooked;

    /** How long to stay: the given seconds, kept between 3 and 30; 8 when missing or not a number. */
    static int seconds(String s) {
        try { return Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, Integer.parseInt(s.trim()))); }
        catch (RuntimeException e) { return DEFAULT_SECONDS; }
    }

    /** "Title: text", or whichever of the two there is; empty when both are blank. Long input is cut. */
    static String line(String title, String text) {
        String t = cut(title, MAX_TITLE), m = cut(text, MAX_TEXT);
        if (t.isEmpty()) return m;
        return m.isEmpty() ? t : t + ": " + m;
    }

    private static String cut(String s, int max) {
        s = s == null ? "" : s.replaceAll("[\r\n]+", " ").trim();
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    /** Keeps track of which of our screens is in front. Safe to call more than once. */
    static void hook(Context c) {
        if (hooked) return;
        hooked = true;
        ((Application) c.getApplicationContext()).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity a) { top = new WeakReference<>(a); }
            @Override public void onActivityPaused(Activity a) { if (top != null && top.get() == a) top = null; }
            @Override public void onActivityCreated(Activity a, Bundle b) { }
            @Override public void onActivityStarted(Activity a) { }
            @Override public void onActivityStopped(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { }
        });
    }

    /** Shows the message for the given seconds. Call from any thread. */
    static void show(Context c, String title, String text, int seconds) {
        String msg = line(title, text);
        if (msg.isEmpty()) return;
        Context app = c.getApplicationContext();
        ui().post(() -> {
            Activity a = top == null ? null : top.get();
            if (a == null || a.isFinishing() || a.isDestroyed()) {
                Toast.makeText(app, msg, Toast.LENGTH_LONG).show();
                return;
            }
            clear();
            TextView v = new TextView(a);
            v.setText(msg);
            v.setTextColor(Color.WHITE);
            v.setTextSize(22);
            v.setMaxLines(3);
            int pad = Ui.dp(a, 18);
            v.setPadding(pad * 3 / 2, pad, pad * 3 / 2, pad);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xE6101014);
            bg.setCornerRadius(Ui.dp(a, 14));
            v.setBackground(bg);
            v.setElevation(Ui.dp(a, 12));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END);
            lp.setMargins(0, Ui.dp(a, 32), Ui.dp(a, 40), 0);
            lp.width = Math.min(Ui.dp(a, 520), a.getResources().getDisplayMetrics().widthPixels * 2 / 3);
            a.addContentView(v, lp);
            shown = v;
            ui().postDelayed(Notice::clear, seconds * 1000L);
        });
    }

    private static void clear() {
        if (shown != null && shown.getParent() instanceof ViewGroup) ((ViewGroup) shown.getParent()).removeView(shown);
        shown = null;
    }
}
