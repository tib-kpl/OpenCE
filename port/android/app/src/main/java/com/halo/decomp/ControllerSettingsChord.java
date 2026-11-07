package com.halo.decomp;

import java.util.HashMap;
import java.util.Map;

/** Start/Select singles reach the game; a pair held on the same pad opens settings. */
final class ControllerSettingsChord {
    interface Output {
        void key(int device, int button, boolean down);
        void open();
    }
    private static final class Pad {
        int held, delivered;
        boolean opened;
    }
    private final Map<Integer, Pad> pads = new HashMap<>();
    private final Output output;

    ControllerSettingsChord(Output output) { this.output = output; }

    void key(int device, int button, boolean down) {
        if (button < 0 || button > 1) throw new IllegalArgumentException("button");
        int bit = 1 << button;
        Pad pad = pads.get(device);
        if (pad == null) {
            if (!down) return;
            pad = new Pad();
            pads.put(device, pad);
        }
        if (down) {
            if ((pad.held & bit) != 0) return;
            pad.held |= bit;
            if (pad.held == 3 && !pad.opened) {
                releaseDelivered(device, pad);
                pad.opened = true;
                output.open();
            }
        } else if ((pad.held & bit) != 0) {
            if (!pad.opened) {
                if ((pad.delivered & bit) == 0) output.key(device, button, true);
                output.key(device, button, false);
            }
            pad.held &= ~bit;
            pad.delivered &= ~bit;
            if (pad.held == 0) pads.remove(device);
        }
    }

    void flush() {
        for (Map.Entry<Integer, Pad> entry : pads.entrySet()) {
            Pad pad = entry.getValue();
            if (pad.opened) continue;
            int pending = pad.held & ~pad.delivered;
            pad.delivered |= pending;
            for (int button = 0; button < 2; button++)
                if ((pending & (1 << button)) != 0) output.key(entry.getKey(), button, true);
        }
    }

    private void releaseDelivered(int device, Pad pad) {
        for (int button = 0; button < 2; button++)
            if ((pad.delivered & (1 << button)) != 0) output.key(device, button, false);
        pad.delivered = 0;
    }

    void disconnect(int device) {
        Pad pad = pads.remove(device);
        if (pad != null) releaseDelivered(device, pad);
    }

    void cancel() {
        for (Map.Entry<Integer, Pad> entry : pads.entrySet()) releaseDelivered(entry.getKey(), entry.getValue());
        pads.clear();
    }
}
