package com.halo.decomp;

import android.os.Handler;
import android.view.InputDevice;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Production Android adapter with a deterministic event queue and no SDL surface. */
public final class AndroidSettingsShortcutTest {
    private final List<String> events = new ArrayList<>();
    private final AndroidSettingsShortcut shortcut = new AndroidSettingsShortcut(() -> events.add("settings"),
        event -> events.add(event.getDeviceId() + ":" + event.getKeyCode() + (event.getAction() == 0 ? "+" : "-")));
    private KeyEvent event(int device, int button, boolean down) {
        return new KeyEvent(device, button, down ? 0 : 1, InputDevice.SOURCE_GAMEPAD);
    }
    private void key(int device, int button, boolean down) {
        if (!shortcut.key(event(device, button, down))) throw new AssertionError("pad key ignored");
    }
    private void expect(String... wanted) {
        if (!events.equals(Arrays.asList(wanted))) throw new AssertionError(events + " != " + Arrays.toString(wanted));
        events.clear();
    }
    private void run() {
        for (int first : new int[]{108, 109}) {
            int second = first == 108 ? 109 : 108;
            key(7, first, true); key(7, second, true);
            key(7, first, false); key(7, second, false);
            Handler.advance(1000);
            expect("settings");
        }
        key(7, 108, true); key(7, 108, false);
        expect("7:108+");
        Handler.advance(49); expect();
        Handler.advance(1); expect("7:108-"); // SDL sees the tap across an input update.
        key(7, 109, true);
        Handler.advance(250); expect("7:109+");
        key(7, 108, true); expect("7:109-", "settings"); // late chord releases before opening.
        shortcut.reset(); Handler.advance(500); expect();
        key(7, 108, true); Handler.advance(250); expect("7:108+");
        shortcut.reset(); expect("7:108-"); // no delayed up may leak after losing focus.
        Handler.advance(500); expect();
        key(7, 109, true); Handler.advance(250); expect("7:109+");
        shortcut.key(event(7, 109, false).cancel()); expect("7:109-");
        Handler.advance(500); expect();
        key(7, 108, true); shortcut.disconnect(7); Handler.advance(300); expect();
        key(7, 108, true); key(8, 109, true);
        Handler.advance(250); expect("7:108+", "8:109+");
        shortcut.disconnect(7); shortcut.disconnect(8); expect("7:108-", "8:109-");
        if (shortcut.key(new KeyEvent(0, 4, 0, InputDevice.SOURCE_KEYBOARD)))
            throw new AssertionError("ordinary Android Back was intercepted");
        if (shortcut.key(event(7, 96, true))) throw new AssertionError("ordinary A was intercepted");
        shortcut.reset();
        System.out.println("Android shortcut timing, cancellation, SDL tap duration and source checks passed");
    }
    public static void main(String[] args) { new AndroidSettingsShortcutTest().run(); }
}
