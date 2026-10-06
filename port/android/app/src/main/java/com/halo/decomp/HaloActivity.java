package com.halo.decomp;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
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

    /** lets system link's broadcasts in over Wi-Fi while the game runs */
    private WifiManager.MulticastLock multicastLock;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideSystemBars = this::hideSystemUi;

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
        hideSystemUiSoon();
        acquireMulticastLock();
        // a new version looked for while the game starts
        Updater.start(this);
    }

    // ---------- full screen

    /**
     * The game is always full screen (sdl_platform.c asks for it), but SDL
     * starts the window as an ordinary one (SDLActivity.onCreate) and only
     * hides the system bars when the game asks, with the flags Android has
     * deprecated; it does not hide them again when something takes them
     * back: the soft keyboard, a dialog, the notification shade. So the bars
     * are hidden here, with the window insets controller (the flags below
     * Android 11), at start-up, whenever the window gets the focus back, on
     * resume and whenever the bars show up: swiping from an edge still shows
     * them for a moment.
     */
    private void hideSystemUi() {
        Window window = getWindow();

        if (window == null)
            return;
        View decor = window.getDecorView();

        // (SDLActivity.onSystemUiVisibilityChange hides them again as well)
        mFullscreenModeActive = true;
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 28) {
            // the game draws under a display's notch or camera hole
            WindowManager.LayoutParams attributes = window.getAttributes();

            attributes.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();

            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    /**
     * Hides the bars now and again shortly after: SDL's own window style
     * message, posted by its onCreate, arrives after this activity's and
     * shows them.
     */
    private void hideSystemUiSoon() {
        handler.removeCallbacks(hideSystemBars);
        handler.post(hideSystemBars);
        handler.postDelayed(hideSystemBars, 400);
        handler.postDelayed(hideSystemBars, 1500);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus)
            hideSystemUiSoon();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUiSoon();
    }

    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        super.onSystemUiVisibilityChange(visibility);
        if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0)
            hideSystemUiSoon();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(hideSystemBars);
        if (multicastLock != null && multicastLock.isHeld())
            multicastLock.release();
        multicastLock = null;
        super.onDestroy();
    }

    /**
     * Many phones drop the Wi-Fi's broadcast and multicast datagrams to
     * save power unless an app holds this: without it they would not see
     * system link games on the local network, nor be seen hosting one.
     */
    private void acquireMulticastLock() {
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi == null)
                return;
            multicastLock = wifi.createMulticastLock("halo-system-link");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        } catch (RuntimeException e) {
            // (no Wi-Fi, or not allowed: the local network may miss games)
            multicastLock = null;
        }
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
