# Android shortcut test fixtures

These minimal API fixtures run the production `AndroidSettingsShortcut.java`
on a JDK without an Android device. Handler and SystemClock provide a
deterministic queue; KeyEvent provides the fields and source masks that the
adapter reads. TouchControls reports no physical inventory so the tests use
the event's gamepad/keyboard source explicitly.

They are compiled only by `tools/test_touch_layout.py` into
`build/touch-layout-tests`. They are not application sources or APK assets.
`AndroidSettingsShortcutTest.java` verifies timing, key balancing, source
filtering and cancellation; it does not substitute a copy of the adapter.
