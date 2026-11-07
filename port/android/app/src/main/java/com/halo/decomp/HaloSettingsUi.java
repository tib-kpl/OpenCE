package com.halo.decomp;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared Android setup screen: disc import and the in-game gear use the same shell. */
final class HaloSettingsUi {
    static final int INK = Color.rgb(6, 13, 23);
    static final int PANEL = Color.rgb(13, 28, 43);
    static final int CYAN = Color.rgb(114, 218, 247);
    static final int WHITE = Color.rgb(233, 242, 250);
    static final int MUTED = Color.rgb(147, 171, 192);
    final Activity activity;
    final LinearLayout navigation, content;
    final TextView current, status;
    final Button primary;
    private final Typeface font;
    private final java.util.ArrayList<Button> tabs = new java.util.ArrayList<>();
    private final java.util.ArrayList<Runnable> tabActions = new java.util.ArrayList<>();
    private int selectedTab;
    private long lastAxis;
    private final ControllerSettingsChord closeChord;

    HaloSettingsUi(Activity activity, String title, String subtitle, String action, Runnable run) {
        this.activity = activity;
        closeChord = new ControllerSettingsChord(new ControllerSettingsChord.Output() {
            @Override public void key(int device, int button, boolean down) {}
            @Override public void open() { activity.finish(); }
        });
        Typeface loaded;
        try { loaded = Typeface.createFromAsset(activity.getAssets(), "fonts/Overpass-750.ttf"); }
        catch (RuntimeException missing) { loaded = Typeface.DEFAULT; }
        font = loaded;
        activity.getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        FrameLayout backdrop = new Backdrop(activity);
        LinearLayout root = column();
        root.setPadding(dp(28), dp(18), dp(28), dp(14));
        backdrop.addView(root, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout heading = column();
        heading.addView(label("OPENCE  /  ANDROID", 10, CYAN));
        heading.addView(label(title, 28, WHITE));
        heading.addView(label(subtitle, 11, MUTED));
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        current = label("", 11, CYAN);
        current.setGravity(Gravity.END);
        current.setMaxWidth(dp(300));
        current.setMaxLines(2);
        current.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(current);
        TextView shoulders = label("L1  /  R1", 11, MUTED);
        shoulders.setPadding(dp(18), 0, 0, 0);
        header.addView(shoulders);
        root.addView(header);
        root.addView(rule(), new LinearLayout.LayoutParams(-1, dp(1)));

        LinearLayout body = new LinearLayout(activity);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, 0, 1);
        bodyParams.topMargin = dp(16);
        bodyParams.bottomMargin = dp(12);
        root.addView(body, bodyParams);
        navigation = column();
        navigation.setPadding(0, 0, dp(18), 0);
        ScrollView rail = new ScrollView(activity);
        rail.setVerticalScrollBarEnabled(false);
        rail.addView(navigation);
        body.addView(rail, new LinearLayout.LayoutParams(dp(158), -1));
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, dp(6), 0);
        content = column();
        scroll.addView(content);
        body.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));

        root.addView(rule(), new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout footer = new LinearLayout(activity);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, dp(8), 0, 0);
        status = label("", 11, MUTED);
        status.setMaxLines(2);
        footer.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        primary = button(action, run);
        footer.addView(primary, new LinearLayout.LayoutParams(dp(190), dp(42)));
        root.addView(footer);
        activity.setContentView(backdrop);
    }

    int dp(float size) { return Math.round(size * activity.getResources().getDisplayMetrics().density); }
    LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    TextView label(String value, float size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(font);
        return view;
    }
    private View rule() {
        View view = new View(activity);
        view.setBackgroundColor(Color.rgb(33, 61, 80));
        return view;
    }
    private GradientDrawable surface(int fill, int border, int width) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(fill);
        result.setCornerRadius(dp(3));
        result.setStroke(dp(width), border);
        return result;
    }
    Button button(String text, Runnable action) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextSize(13);
        button.setTypeface(font);
        button.setAllCaps(false);
        button.setPadding(dp(14), dp(4), dp(14), dp(4));
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setStateListAnimator(null);
        button.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}},
            new int[]{MUTED, WHITE}));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, surface(0xff244b60, CYAN, 2));
        states.addState(new int[]{android.R.attr.state_focused}, surface(0xff1c3b51, CYAN, 2));
        states.addState(new int[]{android.R.attr.state_selected}, surface(0xff173345, 0xff4e9bb3, 1));
        states.addState(new int[]{}, surface(PANEL, 0xff294357, 1));
        button.setBackground(states);
        button.setOnClickListener(v -> action.run());
        return button;
    }
    void tab(int number, String text, boolean active, Runnable action) {
        Button button = button(String.format(java.util.Locale.ROOT, "%02d  %s", number, text), action);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setSelected(active);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.bottomMargin = dp(8);
        navigation.addView(button, params);
        if (active) selectedTab = tabs.size();
        tabs.add(button);
        tabActions.add(action);
    }
    void heading(String eyebrow, String title, String summary) {
        content.addView(label(eyebrow, 10, CYAN));
        TextView heading = label(title, 23, WHITE);
        heading.setPadding(0, dp(6), 0, dp(5));
        content.addView(heading);
        TextView subheading = label(summary, 12, MUTED);
        subheading.setPadding(0, 0, 0, dp(16));
        content.addView(subheading);
    }
    Button row(String title, String detail, boolean selected, String help, Runnable action) {
        Button row = button(title + "\n" + detail, action);
        row.setSelected(selected);
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        if (!help.isEmpty()) row.setTooltipText(help);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(62));
        params.bottomMargin = dp(7);
        content.addView(row, params);
        return row;
    }

    void choice(String title, String[] labels, int initial, java.util.function.IntPredicate changed) {
        final int[] selected = {Math.max(0, Math.min(labels.length - 1, initial))};
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(surface(PANEL, 0xff294357, 1));
        TextView caption = label(title, 14, WHITE);
        caption.setPadding(dp(14), 0, dp(10), 0);
        row.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));
        Button value = button(labels[selected[0]], () -> {});
        java.util.function.IntConsumer adjust = direction -> {
            int next = (selected[0] + direction + labels.length) % labels.length;
            if (!changed.test(next)) return;
            selected[0] = next;
            value.setText(labels[next]);
        };
        Button left = button("‹", () -> adjust.accept(-1));
        Button right = button("›", () -> adjust.accept(1));
        left.setFocusable(false);
        right.setFocusable(false);
        left.setContentDescription(title + " : " + "−");
        right.setContentDescription(title + " : " + "+");
        value.setOnClickListener(v -> adjust.accept(1));
        value.setOnKeyListener((v, key, event) -> {
            if (key != KeyEvent.KEYCODE_DPAD_LEFT && key != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) adjust.accept(key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            return true;
        });
        row.addView(left, new LinearLayout.LayoutParams(dp(40), dp(42)));
        row.addView(value, new LinearLayout.LayoutParams(dp(180), dp(42)));
        row.addView(right, new LinearLayout.LayoutParams(dp(40), dp(42)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(54));
        params.bottomMargin = dp(7);
        content.addView(row, params);
    }

    boolean key(KeyEvent event) {
        int key = event.getKeyCode();
        boolean controller = event.isFromSource(android.view.InputDevice.SOURCE_GAMEPAD)
            || event.isFromSource(android.view.InputDevice.SOURCE_JOYSTICK)
            || TouchControls.isController(event.getDevice());
        if (controller && activity.getIntent().getBooleanExtra(GraphicsActivity.EXTRA_FROM_GAME, false)
            && (key == KeyEvent.KEYCODE_BUTTON_START || key == KeyEvent.KEYCODE_BUTTON_SELECT || key == KeyEvent.KEYCODE_BACK)) {
            if (event.isCanceled()) closeChord.cancel();
            else closeChord.key(event.getDeviceId(), key == KeyEvent.KEYCODE_BUTTON_START ? 0 : 1,
                event.getAction() == KeyEvent.ACTION_DOWN);
            return true;
        }
        if (key == KeyEvent.KEYCODE_BUTTON_L1 || key == KeyEvent.KEYCODE_BUTTON_R1) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0 && !tabs.isEmpty()) {
                int next = (selectedTab + (key == KeyEvent.KEYCODE_BUTTON_L1 ? -1 : 1) + tabs.size()) % tabs.size();
                tabActions.get(next).run();
            }
            return true;
        }
        if (key == KeyEvent.KEYCODE_BUTTON_A) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                View focus = activity.getCurrentFocus();
                if (focus != null && focus.isEnabled()) focus.performClick();
            }
            return true;
        }
        if (key == KeyEvent.KEYCODE_BUTTON_B) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) activity.finish();
            return true;
        }
        return false;
    }

    boolean motion(MotionEvent event) {
        if ((event.getSource() & android.view.InputDevice.SOURCE_JOYSTICK) == 0) return false;
        float x = event.getAxisValue(MotionEvent.AXIS_HAT_X), y = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if (Math.abs(x) < .5f) x = event.getAxisValue(MotionEvent.AXIS_X);
        if (Math.abs(y) < .5f) y = event.getAxisValue(MotionEvent.AXIS_Y);
        if (Math.max(Math.abs(x), Math.abs(y)) < .6f) { lastAxis = 0; return true; }
        long now = android.os.SystemClock.uptimeMillis();
        if (lastAxis != 0 && now - lastAxis < 180) return true;
        lastAxis = now;
        int key = Math.abs(y) >= Math.abs(x) ? (y < 0 ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN)
            : (x < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT);
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
        return true;
    }

    private static final class Backdrop extends FrameLayout {
        private final Paint paint = new Paint(3);
        Backdrop(Context context) { super(context); setWillNotDraw(false); setBackgroundColor(INK); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth(), height = getHeight();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1);
            paint.setColor(0xff102332);
            float step = 40 * getResources().getDisplayMetrics().density;
            for (float x = 0; x < width; x += step) canvas.drawLine(x, 0, x, height, paint);
            for (float y = 0; y < height; y += step) canvas.drawLine(0, y, width, y, paint);
            paint.setColor(0xff173246);
            paint.setStrokeWidth(height * .025f);
            canvas.drawOval(width * .64f, height * -.45f, width * 1.2f, height * 1.35f, paint);
            paint.setStrokeWidth(1);
            paint.setColor(0xff29546c);
            canvas.drawOval(width * .63f, height * -.47f, width * 1.21f, height * 1.37f, paint);
        }
    }
}
