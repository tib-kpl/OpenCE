package com.halo.decomp;

import android.os.Bundle;
import android.view.Display;
import android.view.WindowManager;

import org.libsdl.app.SDLActivity;

/**
 * The game: SDL3's activity, running libmain.so (port/android/host), which
 * loads the game image from the APK's assets.
 */
public class HaloActivity extends SDLActivity {
    private static final int PR_SET_DUMPABLE = 4; // <linux/prctl.h>

    /**
     * Makes the process dumpable, as it is in a debuggable build. The host
     * reads /proc/self/pagemap to tell whether ART's large object space over
     * the game's fixed memory range is idle before taking it
     * (host_memory.c, range_unused). Android starts the processes of
     * non-debuggable apps non-dumpable, which gives their /proc/self files
     * to root: pagemap cannot be read, the range is left to ART and a
     * release build stops with "cannot load the game image". The process
     * stays private to its own user.
     */
    static void makeDumpable() {
        try {
            android.system.Os.prctl(PR_SET_DUMPABLE, 1, 0, 0, 0);
        } catch (Exception e) {
            android.util.Log.w("halo", "cannot make the process dumpable: " + e);
        }
    }

    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL3", "main" };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        makeDumpable();
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        preferHighestRefreshRate();
        // a new version looked for while the game starts
        Updater.start(this);
    }

    /**
     * The game draws a frame at every display refresh, between its 30 Hz
     * ticks (port/linux/game/render_interpolation.c); Android otherwise
     * often keeps an app at 60 Hz on a faster display.
     */
    private void preferHighestRefreshRate() {
        Display display = getWindowManager().getDefaultDisplay();
        Display.Mode current = display.getMode();
        Display.Mode best = current;

        for (Display.Mode mode : display.getSupportedModes()) {
            if (mode.getPhysicalWidth() == current.getPhysicalWidth() &&
                mode.getPhysicalHeight() == current.getPhysicalHeight() &&
                mode.getRefreshRate() > best.getRefreshRate()) {
                best = mode;
            }
        }
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.preferredDisplayModeId = best.getModeId();
        getWindow().setAttributes(attributes);
    }
}
