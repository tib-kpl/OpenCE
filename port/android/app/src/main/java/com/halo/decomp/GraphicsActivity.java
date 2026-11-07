package com.halo.decomp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;

/** Launcher access to the same setup panel used as a dialog in the game. */
public class GraphicsActivity extends Activity {
    static final String EXTRA_FROM_GAME = AndroidSettingsPanel.EXTRA_FROM_GAME;
    static final String CUSTOM_ARCHIVE = AndroidSettingsPanel.CUSTOM_ARCHIVE;
    private AndroidSettingsPanel panel;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        panel = new AndroidSettingsPanel(this, state, false, this::finish);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (panel != null) panel.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        return (panel != null && panel.key(event)) || super.dispatchKeyEvent(event);
    }

    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        return (panel != null && panel.motion(event)) || super.onGenericMotionEvent(event);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (panel != null) panel.activityResult(request, result, data);
    }

    @Override protected void onDestroy() {
        if (panel != null) panel.dispose();
        super.onDestroy();
    }
}
