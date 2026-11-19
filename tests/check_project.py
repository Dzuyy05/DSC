"""Fast source-level sanity tests; no Android SDK needed.

Run: python3 tests/check_project.py
These checks do not replace compiling or testing on a real Xperia.
"""

from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main"
MANIFEST = APP / "AndroidManifest.xml"
PKG = APP / "java/com/aowvn/cybershotclassic"


def main():
    assert ROOT.joinpath("settings.gradle").exists()
    assert ROOT.joinpath("app/build.gradle").exists()
    manifest = ET.parse(MANIFEST).getroot()
    ns = {"a": "http://schemas.android.com/apk/res/android"}
    permissions = [p.get("{http://schemas.android.com/apk/res/android}name")
                   for p in manifest.findall("uses-permission")]
    assert "android.permission.CAMERA" in permissions
    assert "android.permission.INTERNET" not in permissions
    activity = manifest.find("application/activity")
    assert activity is not None
    assert activity.get("{http://schemas.android.com/apk/res/android}screenOrientation") == "landscape"

    files = [PKG / (name + ".java")
             for name in ("MainActivity", "ClassicCamera", "ViewfinderOverlay")]
    for path in files:
        assert path.exists(), str(path)
        code = path.read_text(encoding="utf-8")
        assert "package com.aowvn.cybershotclassic;" in code
        assert code.count("{") == code.count("}"), str(path)
        assert not re.search(r"<<<<<<<|>>>>>>>|=======", code)

    activity_code = files[0].read_text()
    camera_code = files[1].read_text()
    assert "KEYCODE_FOCUS" in activity_code and "KEYCODE_CAMERA" in activity_code
    assert "CAMERA" in activity_code and "requestPermissions" in activity_code
    assert "MediaStore.Images.Media.IS_PENDING" in camera_code
    assert "CONTROL_AF_TRIGGER_START" in camera_code
    assert "TEMPLATE_STILL_CAPTURE" in camera_code
    print("PASS: manifest, source structure, key events, camera capture and save path")


if __name__ == "__main__":
    main()
