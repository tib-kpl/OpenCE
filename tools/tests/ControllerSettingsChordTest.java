package com.halo.decomp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Balanced game input and one settings action per physical controller chord. */
public final class ControllerSettingsChordTest {
    private final List<String> output = new ArrayList<>();
    private final ControllerSettingsChord chord = new ControllerSettingsChord(new ControllerSettingsChord.Output() {
        public void key(int device, int button, boolean down) { output.add(device + ":" + button + (down ? "+" : "-")); }
        public void open() { output.add("settings"); }
    });
    private void expect(String... expected) {
        if (!output.equals(Arrays.asList(expected))) throw new AssertionError(output + " != " + Arrays.toString(expected));
        output.clear();
    }
    private void run() {
        for (int first = 0; first < 2; first++) {
            chord.key(7, first, true);
            chord.key(7, 1 - first, true);
            chord.key(7, first, true);
            chord.flush();
            chord.key(7, first, false);
            chord.key(7, 1 - first, false);
            expect("settings");
            chord.key(7, first, true);
            chord.key(7, first, false);
            expect("7:" + first + "+", "7:" + first + "-");
        }
        chord.key(7, 0, true);
        chord.flush(); chord.flush();
        chord.key(7, 0, false);
        expect("7:0+", "7:0-");
        chord.key(7, 1, true);
        chord.flush();
        chord.key(7, 0, true);
        chord.key(7, 1, false);
        chord.key(7, 0, false);
        expect("7:1+", "7:1-", "settings");
        chord.key(7, 0, true);
        chord.key(8, 1, true);
        chord.key(7, 0, false);
        chord.key(8, 1, false);
        expect("7:0+", "7:0-", "8:1+", "8:1-");
        chord.key(7, 0, true);
        chord.cancel();
        chord.flush(); chord.key(7, 0, false);
        expect();
        chord.key(7, 1, true);
        chord.flush(); chord.disconnect(7);
        chord.key(7, 1, false);
        expect("7:1+", "7:1-");
        chord.key(7, 0, true); chord.key(7, 1, true);
        chord.cancel(); chord.key(7, 0, false); chord.key(7, 1, false);
        expect("settings");
        System.out.println("Controller settings chord, lifecycle and device isolation checks passed");
    }
    public static void main(String[] args) { new ControllerSettingsChordTest().run(); }
}
