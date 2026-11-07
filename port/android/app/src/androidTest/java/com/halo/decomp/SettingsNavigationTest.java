package com.halo.decomp;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.File;

/** Runs against a disposable .test app: real windows, focus, input and config writes. */
public final class SettingsNavigationTest extends Instrumentation {
    private Activity activity;
    private AndroidSettingsPanel overlay;
    private boolean french;

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (!getTargetContext().getPackageName().endsWith(".test"))
                throw new AssertionError("Use a disposable HALO_APPLICATION_ID_SUFFIX=.test build");
            french = "fr".equals(getTargetContext().getResources().getConfiguration().getLocales().get(0).getLanguage());
            exercise();
            result.putString("stream", "PASS: settings controller navigation, touch recovery, scrolling and overlay\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private String t(String fr, String en) { return french ? fr : en; }

    private Activity launch(Class<? extends Activity> type, boolean fromGame) {
        Intent intent = new Intent(getTargetContext(), type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("settings_page", 3).putExtra(AndroidSettingsPanel.EXTRA_FROM_GAME, fromGame);
        Activity opened = startActivitySync(intent);
        runOnMainSync(() -> opened.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        waitForIdleSync();
        return opened;
    }

    private AccessibilityNodeInfo focused() {
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        return root == null ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
    }

    private void expect(String prefix) {
        long deadline = SystemClock.uptimeMillis() + 3000;
        String actual = "no focus";
        do {
            waitForIdleSync();
            AccessibilityNodeInfo node = focused();
            if (node != null) {
                actual = String.valueOf(node.getText());
                if (node.isEnabled() && actual.startsWith(prefix)) return;
            }
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Expected focus on " + prefix + ", got " + actual);
    }

    private void edge(int key, boolean down) {
        long now = SystemClock.uptimeMillis();
        sendKeySync(new KeyEvent(now, now, down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP,
            key, 0, 0, -1, 0, 0, InputDevice.SOURCE_GAMEPAD));
        waitForIdleSync();
    }

    private void key(int key) { edge(key, true); edge(key, false); }
    private void up() { key(KeyEvent.KEYCODE_DPAD_UP); }
    private void down() { key(KeyEvent.KEYCODE_DPAD_DOWN); }
    private void left() { key(KeyEvent.KEYCODE_DPAD_LEFT); }
    private void right() { key(KeyEvent.KEYCODE_DPAD_RIGHT); }

    private void tapFocus() {
        Rect bounds = new Rect();
        focused().getBoundsInScreen(bounds);
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, bounds.centerX(), bounds.centerY(), 0);
        MotionEvent up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, bounds.centerX(), bounds.centerY(), 0);
        sendPointerSync(down); sendPointerSync(up);
        down.recycle(); up.recycle();
        waitForIdleSync();
    }

    private void exercise() throws Exception {
        File root = getTargetContext().getExternalFilesDir(null);
        File config = new File(root, "config.toml");
        if (config.exists() && !config.delete()) throw new AssertionError("Cannot reset test config");
        activity = launch(GraphicsActivity.class, false);
        expect("2160p");
        right(); expect("1440p"); left(); expect("2160p");
        key(KeyEvent.KEYCODE_BUTTON_A); expect("1440p");
        if (!"\"1440\"".equals(Updater.readSetting(config, "display", "resolution_limit")))
            throw new AssertionError("The controller did not save the resolution");
        down(); expect("100 %"); left(); expect("Xbox"); right(); expect("100 %");
        down(); expect(t("Activée", "On")); left(); expect(t("Désactivée", "Off"));
        right(); down(); expect(t("Activée", "On")); // disabled Start is skipped
        up(); up(); up(); expect("04"); right(); expect("1440p");
        up(); up(); expect("03"); right(); expect(t("Pilote de la console", "Console driver"));
        key(KeyEvent.KEYCODE_BUTTON_A);
        down(); down(); expect(t("Importer un pilote", "Import driver"));
        left(); up(); right(); expect("Vulkan");
        right(); expect("OpenGL ES"); left(); expect("Vulkan");
        key(KeyEvent.KEYCODE_BUTTON_L1); expect(t("Importer le jeu", "Import game data"));
        key(KeyEvent.KEYCODE_BUTTON_R1); expect("Vulkan");
        key(KeyEvent.KEYCODE_BUTTON_R1); key(KeyEvent.KEYCODE_BUTTON_R1); expect("1440p");
        tapFocus();
        down(); expect("1080p"); // first direction recovers focus after touch
        down(); expect("100 %");
        runOnMainSync(() -> activity.finish());

        activity = launch(GraphicsActivity.class, true);
        expect("1080p"); down(); down(); down(); expect(t("B  Reprendre", "B  Resume"));
        up(); expect(t("Activée", "On"));
        final int[] closed = {0};
        Bundle state = new Bundle(); state.putInt("settings_page", 3);
        runOnMainSync(() -> overlay = new AndroidSettingsPanel(activity, state, true, () -> closed[0]++));
        expect("1080p"); right(); expect("720p"); down(); down(); down(); expect(t("B  Reprendre", "B  Resume"));
        key(KeyEvent.KEYCODE_BUTTON_B);
        if (closed[0] != 1 || activity.isFinishing()) throw new AssertionError("B did not close just the overlay");
        runOnMainSync(() -> overlay = new AndroidSettingsPanel(activity, state, true, () -> closed[0]++));
        expect("720p");
        edge(KeyEvent.KEYCODE_BUTTON_START, true); edge(KeyEvent.KEYCODE_BUTTON_SELECT, true);
        edge(KeyEvent.KEYCODE_BUTTON_START, false); edge(KeyEvent.KEYCODE_BUTTON_SELECT, false);
        if (closed[0] != 2) throw new AssertionError("Start + Select did not close the overlay");
        runOnMainSync(() -> activity.finish());

        File drivers = new File(root, Updater.DRIVER_FOLDER);
        drivers.mkdirs();
        for (int i = 0; i < 20; i++) new File(drivers, String.format(java.util.Locale.ROOT, "fixture-%02d.zip", i)).createNewFile();
        activity = launch(GraphicsActivity.class, false);
        key(KeyEvent.KEYCODE_BUTTON_L1); expect(t("Pilote de la console", "Console driver"));
        for (int i = 0; i < 22; i++) down();
        expect("fixture-19.zip");
        Rect bounds = new Rect(); focused().getBoundsInScreen(bounds);
        if (bounds.isEmpty() || bounds.bottom > getTargetContext().getResources().getDisplayMetrics().heightPixels)
            throw new AssertionError("The focused driver did not scroll into view");
        left(); expect("03");
        runOnMainSync(() -> activity.finish());
        activity = launch(LauncherActivity.class, false);
        expect(t("Importer le jeu", "Import game data"));
        left(); expect("01"); down(); right(); expect("Vulkan");
        runOnMainSync(() -> activity.finish());
    }
}
