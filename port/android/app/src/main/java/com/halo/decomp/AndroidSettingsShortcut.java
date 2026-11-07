package com.halo.decomp;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import java.util.HashMap;
import java.util.Map;

/** Defers a single Start/Select briefly so a quick chord never pauses the game. */
final class AndroidSettingsShortcut {
    interface Relay { void send(KeyEvent event); }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer, KeyEvent[]> originals = new HashMap<>();
    private final ControllerSettingsChord chord;
    private final Runnable flush;
    private final Map<Long, Long> pressedAt = new HashMap<>();
    private final Map<Long, Runnable> releases = new HashMap<>();
    private final Relay relay;
    private boolean replaying;

    AndroidSettingsShortcut(Runnable open, Relay relay) {
        this.relay = relay;
        chord = new ControllerSettingsChord(new ControllerSettingsChord.Output() {
            @Override public void key(int device, int button, boolean down) {
                KeyEvent[] events = originals.get(device);
                if (events == null || events[button] == null) return;
                long id = ((long)device << 32) | button;
                KeyEvent forwarded = KeyEvent.changeAction(events[button], down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP);
                if (down) {
                    Runnable release = releases.get(id);
                    if (release != null) { handler.removeCallbacks(release); release.run(); }
                    pressedAt.put(id, SystemClock.uptimeMillis());
                    send(forwarded);
                } else {
                    Long began = pressedAt.remove(id);
                    // SDL polls button state: an immediate down/up replay could
                    // otherwise disappear between the game's input updates.
                    long delay = began == null ? 0 : Math.max(0, 50 - (SystemClock.uptimeMillis() - began));
                    if (delay == 0) send(forwarded);
                    else {
                        Runnable release = () -> { releases.remove(id); send(forwarded); };
                        releases.put(id, release);
                        handler.postDelayed(release, delay);
                    }
                }
            }
            @Override public void open() {
                handler.removeCallbacks(flush);
                releasePending(null);
                open.run();
            }
        });
        flush = chord::flush;
    }

    private void send(KeyEvent event) {
        replaying = true;
        try { relay.send(KeyEvent.changeTimeRepeat(event, SystemClock.uptimeMillis(), 0)); }
        finally { replaying = false; }
    }

    private void releasePending(Integer device) {
        for (Long id : new java.util.ArrayList<>(releases.keySet())) {
            if (device != null && (int)(id >> 32) != device) continue;
            Runnable release = releases.get(id);
            handler.removeCallbacks(release);
            release.run();
        }
    }

    boolean key(KeyEvent event) {
        if (replaying) return false;
        boolean controller = event.isFromSource(InputDevice.SOURCE_GAMEPAD)
            || event.isFromSource(InputDevice.SOURCE_JOYSTICK)
            || TouchControls.isController(event.getDevice());
        if (!controller) return false;
        int code = event.getKeyCode();
        int button = code == KeyEvent.KEYCODE_BUTTON_START ? 0
            : code == KeyEvent.KEYCODE_BUTTON_SELECT || code == KeyEvent.KEYCODE_BACK ? 1 : -1;
        if (button < 0 || (event.getAction() != KeyEvent.ACTION_DOWN && event.getAction() != KeyEvent.ACTION_UP)) return false;
        int device = event.getDeviceId();
        KeyEvent[] events = originals.get(device);
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() != 0) return true;
            if (events == null) { events = new KeyEvent[2]; originals.put(device, events); }
            // A new physical press ends a stale press whose key-up was lost.
            if (events[button] != null) chord.key(device, button, false);
            events[button] = new KeyEvent(event);
            chord.key(device, button, true);
            handler.removeCallbacks(flush);
            handler.postDelayed(flush, 250);
        } else if (events != null && events[button] != null) {
            if (event.isCanceled()) disconnect(device);
            else chord.key(device, button, false);
            events[button] = null;
            if (events[0] == null && events[1] == null) originals.remove(device);
        }
        return true;
    }

    void disconnect(int device) {
        chord.disconnect(device);
        releasePending(device);
        originals.remove(device);
    }

    void reset() {
        handler.removeCallbacks(flush);
        chord.cancel();
        releasePending(null);
        pressedAt.clear();
        originals.clear();
    }
}
