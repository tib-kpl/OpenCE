package android.view;
public final class KeyEvent {
    public static final int ACTION_DOWN = 0, ACTION_UP = 1;
    public static final int KEYCODE_BUTTON_START = 108, KEYCODE_BUTTON_SELECT = 109, KEYCODE_BACK = 4;
    private int device, button, action, source, repeat;
    private boolean canceled;
    public KeyEvent(int device, int button, int action, int source) {
        this.device = device; this.button = button; this.action = action; this.source = source;
    }
    public KeyEvent(KeyEvent other) {
        this(other.device, other.button, other.action, other.source);
        repeat = other.repeat; canceled = other.canceled;
    }
    public KeyEvent cancel() { canceled = true; return this; }
    public boolean isFromSource(int value) { return (source & value) == value; }
    public InputDevice getDevice() { return null; }
    public int getDeviceId() { return device; }
    public int getKeyCode() { return button; }
    public int getAction() { return action; }
    public int getRepeatCount() { return repeat; }
    public boolean isCanceled() { return canceled; }
    public static KeyEvent changeAction(KeyEvent event, int action) {
        KeyEvent copy = new KeyEvent(event); copy.action = action; return copy;
    }
    public static KeyEvent changeTimeRepeat(KeyEvent event, long time, int repeat) {
        KeyEvent copy = new KeyEvent(event); copy.repeat = repeat; return copy;
    }
}
