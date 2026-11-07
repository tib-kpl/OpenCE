#!/usr/bin/env python3
"""Compile and run the touch layout regression checks with JDK 17+."""
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parent.parent
classes = root / "build/touch-layout-tests"
classes.mkdir(parents=True, exist_ok=True)
subprocess.run([
    "javac", "--release", "17", "-d", str(classes),
    str(root / "port/android/app/src/main/java/com/halo/decomp/TouchLayout.java"),
    str(root / "port/android/app/src/main/java/com/halo/decomp/GyroscopeAim.java"),
    str(root / "port/android/app/src/main/java/com/halo/decomp/ControllerSettingsChord.java"),
    str(root / "port/android/app/src/main/java/com/halo/decomp/AndroidSettingsShortcut.java"),
    str(root / "tools/tests/AndroidSettingsShortcutTest.java"),
    *(str(path) for path in sorted((root / "tools/tests/android_settings").rglob("*.java"))),
    str(root / "tools/tests/ControllerSettingsChordTest.java"),
    str(root / "tools/tests/GyroscopeAimTest.java"),
    str(root / "tools/tests/TouchLayoutTest.java")
], check=True)
subprocess.run(["java", "-cp", str(classes), "com.halo.decomp.TouchLayoutTest"], check=True)
subprocess.run(["java", "-cp", str(classes), "com.halo.decomp.GyroscopeAimTest"], check=True)
subprocess.run(["java", "-cp", str(classes), "com.halo.decomp.ControllerSettingsChordTest"], check=True)
subprocess.run(["java", "-cp", str(classes), "com.halo.decomp.AndroidSettingsShortcutTest"], check=True)
