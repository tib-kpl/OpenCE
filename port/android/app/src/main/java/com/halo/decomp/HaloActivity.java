package com.halo.decomp;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Insets;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

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
    /**
     * the latest system gesture insets {left, top, right, bottom}, in pixels;
     * written on the UI thread, read by the game's thread: replaced as a
     * whole, never changed in place
     */
    private volatile int[] gestureInsets = new int[] { 0, 0, 0, 0 };
    /** the on-screen touch controls, over SDL's surface */
    private TouchControls touchControls;
    private static final int EXPORT_LAYOUT = 401, IMPORT_LAYOUT = 402;
    private String pendingLayoutExport;

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
        if (savedInstanceState != null)
            pendingLayoutExport = savedInstanceState.getString("pending-layout-export");
        if (mLayout != null) {
            touchControls = new TouchControls(this);
            mLayout.addView(touchControls, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        preferHighestRefreshRate();
        hideSystemUiSoon();
        trackGestureInsets();
        acquireMulticastLock();
        addGraphicsGear();
        // a new version looked for while the game starts
        Updater.start(this);
    }

    /** the system's file picker, for the touch layout; no storage permission */
    public void chooseLayoutFile(boolean export, String configuration) {
        String message = export
            ? "Choose the folder and the name of the touch layout file."
            : "Choose a touch layout file. It replaces your buttons, the look sensitivity and the General settings.";
        new AlertDialog.Builder(this)
            .setTitle(export ? "Export the layout" : "Import a layout")
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(export ? "Choose the place" : "Choose the file", (dialog, which) -> {
                Intent intent = new Intent(export ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(export ? "text/plain" : "*/*");
                if (export) {
                    pendingLayoutExport = configuration;
                    intent.putExtra(Intent.EXTRA_TITLE, "halo-touch-layout.halolayout");
                }
                try {
                    startActivityForResult(intent, export ? EXPORT_LAYOUT : IMPORT_LAYOUT);
                } catch (android.content.ActivityNotFoundException e) {
                    pendingLayoutExport = null;
                    Toast.makeText(this, "This device has no file picker.", Toast.LENGTH_LONG).show();
                }
            })
            .show();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("pending-layout-export", pendingLayoutExport);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (request != EXPORT_LAYOUT && request != IMPORT_LAYOUT) {
            super.onActivityResult(request, result, data);
            return;
        }
        String exported = pendingLayoutExport;
        pendingLayoutExport = null;
        if (result != RESULT_OK || data == null || data.getData() == null)
            return;
        Uri document = data.getData();
        // (the file is read and written away from the UI thread)
        new Thread(() -> {
            try {
                if (request == EXPORT_LAYOUT)
                    writeLayoutFile(document, exported);
                else
                    readLayoutFile(document);
            } catch (Exception e) {
                runOnUiThread(() -> layoutFileError(e));
            }
        }, "halo-touch-layout-file").start();
    }

    private void writeLayoutFile(Uri document, String configuration) throws IOException {
        if (configuration == null)
            throw new IOException("the layout to export is gone");
        try (OutputStream output = getContentResolver().openOutputStream(document, "wt")) {
            if (output == null)
                throw new IOException("the file cannot be opened");
            output.write(configuration.getBytes(StandardCharsets.UTF_8));
        }
        runOnUiThread(() -> Toast.makeText(this, "The layout is exported.", Toast.LENGTH_SHORT).show());
    }

    private void readLayoutFile(Uri document) throws IOException {
        ByteArrayOutputStream contents = new ByteArrayOutputStream();
        try (InputStream input = getContentResolver().openInputStream(document)) {
            if (input == null)
                throw new IOException("the file cannot be opened");
            byte[] buffer = new byte[4096];
            int size;
            while ((size = input.read(buffer)) != -1) {
                if (contents.size() + size > 65536)
                    throw new IOException("the file is too large");
                contents.write(buffer, 0, size);
            }
        }
        String configuration = new String(contents.toByteArray(), StandardCharsets.UTF_8);
        // checked here, then applied to the overlay at once
        TouchLayout.importConfiguration(configuration);
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || touchControls == null)
                return;
            try {
                touchControls.importLayout(configuration);
                Toast.makeText(this, "The layout is imported and saved.", Toast.LENGTH_SHORT).show();
            } catch (IllegalArgumentException e) {
                layoutFileError(e);
            }
        });
    }

    private void layoutFileError(Exception error) {
        if (isFinishing() || isDestroyed())
            return;
        new AlertDialog.Builder(this)
            .setTitle("Touch layout file")
            .setMessage("The operation did not complete: " + error.getMessage())
            .setPositiveButton("OK", null)
            .show();
    }

    // ---------- the graphics gear

    /**
     * A small, faint gear at the top right, over the game at any time: it opens GraphicsActivity (OpenGL ES or
     * Vulkan, the Vulkan driver, a driver to import), whose change applies when the game is started again.
     * A touch on the gear only; the rest of the screen is the game's.
     */
    private void addGraphicsGear() {
        ImageView gear = new ImageView(this);
        int size = (int) (44 * getResources().getDisplayMetrics().density);
        int margin = (int) (6 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.END);

        params.setMargins(0, margin, margin, 0);
        gear.setImageResource(R.drawable.ic_gear);
        gear.setScaleType(ImageView.ScaleType.FIT_CENTER);
        gear.setPadding(size / 6, size / 6, size / 6, size / 6);
        gear.setAlpha(0.3f);
        gear.setContentDescription("Graphics");
        gear.setOnClickListener(v -> startActivity(new Intent(this, GraphicsActivity.class)
            .putExtra(GraphicsActivity.EXTRA_FROM_GAME, true)));
        addContentView(gear, params);
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
    protected void onResume() {
        super.onResume();
        hideSystemUiSoon();
        if (touchControls != null && getWindow().getDecorView().hasWindowFocus())
            touchControls.startDeviceInput();
    }

    @Override
    protected void onPause() {
        if (touchControls != null)
            touchControls.stopDeviceInput();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (touchControls != null) {
            if (hasFocus)
                touchControls.startDeviceInput();
            else
                touchControls.stopDeviceInput();
        }
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus)
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
        if (touchControls != null)
            touchControls.stopDeviceInput();
        if (multicastLock != null && multicastLock.isHeld())
            multicastLock.release();
        multicastLock = null;
        super.onDestroy();
    }

    /**
     * keeps gestureInsets current; Android sends the insets again when the
     * activity rotates (after the game's native code has started on a phone
     * launched from portrait), so a single read at startup would keep the
     * portrait values; the listener hands the insets on so SDL's own
     * handling still sees them
     */
    private void trackGestureInsets() {
        getWindow().getDecorView().setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                gestureInsets = readGestureInsets(insets);
                return view.onApplyWindowInsets(insets);
            }
        });
    }

    @SuppressWarnings("deprecation") // getSystemGestureInsets is the only call on Android 10
    private static int[] readGestureInsets(WindowInsets insets) {
        Insets gesture;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            gesture = insets.getInsets(WindowInsets.Type.systemGestures());
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            gesture = insets.getSystemGestureInsets();
        else
            return new int[] { 0, 0, 0, 0 };
        return new int[] { gesture.left, gesture.top, gesture.right, gesture.bottom };
    }

    /**
     * the edges of the screen where Android keeps its gestures; in sticky
     * full screen the first swipe from an edge only shows the system bars,
     * and Android hands that swipe to the game as an ordinary finger, so
     * the game (port/linux/src/touch_input.c) must ignore touches that
     * begin there; returns a copy of {left, top, right, bottom} in pixels:
     * all 0 before Android 10 (which has no insets) and until the first
     * insets arrive. The game's native code calls this through JNI by name
     * and signature (host_main.c GetMethodID(...,
     * "getSystemGestureInsetsPixels", "()[I")), at every finger down, so
     * it must not be renamed, retyped or removed as unused
     */
    public int[] getSystemGestureInsetsPixels() {
        return gestureInsets.clone();
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
