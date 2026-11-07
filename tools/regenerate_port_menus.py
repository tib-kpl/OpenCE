"""Refresh port-owned menu screens without the Halo PC tag inputs.

The PC-derived widgets stay as they are. Settings, multiplayer screens and
extra bitmap frames come from port_settings.py and ce_menus.py.
"""
import json
import re
from pathlib import Path

import ce_menus
import port_settings
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1] / "port/assets/menus"
BASE_FRAMES = {
    "shell/main_menu/multiplayer_type_select/mp_options": 3,
    "shell/main_menu/settings_select/player_setup/player_profile_edit/profile_options": 9,
}


def main():
    folder = ROOT / "ce"
    for name, lines in {**port_settings.settings_files(), **port_settings.multiplayer_files()}.items():
        (folder / name).write_text("\n".join(lines), encoding="utf-8", newline="\n")
    bitmaps = folder / "bitmaps.xml"
    text = bitmaps.read_text(encoding="utf-8")
    for relative, frames in ce_menus.MENU_EXTRA_FRAMES.items():
        name = relative.removeprefix("shell/")
        pattern = r'(<bitmap name="' + re.escape(name) + r'">\n)(.*?)(\t</bitmap>)'
        match = re.search(pattern, text, re.S)
        if not match:
            raise ValueError(f"missing bitmap {name}")
        original = match[2].splitlines(keepends=True)[:BASE_FRAMES[relative]]
        for number in frames:
            if number is None:
                original.append('\t\t<frame png="ce/port/blank__0.png" width="16" height="16"/>\n')
            else:
                original.append(f'\t\t<frame png="ce/{relative}__{number}.png" width="512" height="256"/>\n')
        text = text[:match.start()] + match[1] + "".join(original) + match[3] + text[match.end():]
    bitmaps.write_text(text, encoding="utf-8", newline="\n")
    strings_file = folder / "strings.xml"
    strings = strings_file.read_text(encoding="utf-8")
    for name, values in port_settings.STRING_OVERRIDES.items():
        pattern = r'(<strings name="' + re.escape(name) + r'"[^>]*>\n).*?(\t</strings>)'
        match = re.search(pattern, strings, re.S)
        if not match:
            continue
        originals = port_settings.OVERRIDE_ORIGINALS.get(name, [None] * len(values))
        lines = []
        for value, original in zip(values, originals):
            for platform, words in (value.items() if isinstance(value, dict) else [(None, value)]):
                attributes = ce_menus.attributes([("text", words), ("map", original), ("platform", platform)])
                lines.append(f"\t\t<string{attributes}/>\n")
        strings = strings[:match.start()] + match[1] + "".join(lines) + match[2] + strings[match.end():]
    strings_file.write_text(strings, encoding="utf-8", newline="\n")
    logo = ROOT / f"ce/port/{port_settings.CREDITS}/credits_logo.png"
    logo.parent.mkdir(parents=True, exist_ok=True)
    artwork = Image.open(ROOT.parent / "icon/opence-icon.png").convert("RGBA")
    artwork = ImageOps.contain(artwork, (160, 160), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (256, 256))
    canvas.alpha_composite(artwork, ((256 - artwork.width) // 2, (256 - artwork.height) // 2))
    canvas.save(logo, optimize=True)
    manifest = ROOT / "menus.json"
    data = json.loads(manifest.read_text(encoding="utf-8"))
    data["files"] = sorted(set(data["files"]) | {f"ce/{p.name}" for p in folder.glob("*.xml")})
    manifest.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
