#!/usr/bin/env python3
"""Writes a language's copy of the PC menus' XML files (port/assets/menus/ce),
from the translation in port/assets/menus/lang/<language>.json:

    python tools/translate_menus.py --lang fr --out port/android/app/src/main/assets/menus_fr

Only the text is translated: every text="..." attribute, and the strings="A|B"
lists of the spinners, whose English text the translation has. All else in a
file (widgets, names, positions, pictures, the strings' order) stays as it
is, so the translated files work as the English ones do; each is checked
against its English one before it is written. A text the translation does
not have stays English: a new or changed text of the menus shows in English
until it is added to the translation.

The game takes the files of a menus folder beside config.toml in place of its
built-in ones (port/linux/src/menu_files.c); the Android app puts them there
when game.language is the language (LauncherActivity, MenuTranslation).

--check lists the texts the translation lacks and its entries no menu has.
"""

import argparse
import html
import json
import re
import sys
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parent.parent
MENUS = ROOT / "port/assets/menus"
FOLDER = "ce"

TEXT = re.compile(r'(\btext=")([^"]*)(")')
STRINGS = re.compile(r'(<widget\b[^>]*?\sstrings=")([^"]*)(")')
LETTERS = re.compile(r"[A-Za-z]{2}")
# what the game's formatting takes: the translation must keep them as they are
FORMATS = re.compile(r"%[-+ #0]*\d*(?:\.\d+)?[sdxXfc]|%controller")


def escape(value):
    """a value as an XML attribute, as the files write them"""
    value = value.replace("&", "&amp;").replace("<", "&lt;").replace('"', "&quot;")
    return value.replace("\t", "&#9;").replace("\n", "&#10;").replace("\r", "&#13;")


def structure(data):
    """the file's elements and attributes, but for the texts"""
    root = ElementTree.fromstring(data)
    return [(element.tag, sorted((key, value) for key, value in element.attrib.items()
                                 if key not in ("text", "strings")), "text" in element.attrib,
             "strings" in element.attrib) for element in root.iter()]


def translate_file(raw, table, seen, missing):
    def text(match):
        english = html.unescape(match.group(2))
        seen.add(("text", english))
        french = table["text"].get(english)
        if french is None:
            if LETTERS.search(english):
                missing.add(english)
            return match.group(0)
        if FORMATS.findall(english) != FORMATS.findall(french):
            raise SystemExit(f"the formats of {english!r} and {french!r} differ")
        return match.group(1) + escape(french) + match.group(3)

    def strings(match):
        english = html.unescape(match.group(2))
        seen.add(("strings", english))
        french = table["strings"].get(english)
        if french is None:
            if LETTERS.search(english):
                missing.add(english)
            return match.group(0)
        if french.count("|") != english.count("|"):
            raise SystemExit(f"the lists {english!r} and {french!r} differ in length")
        return match.group(1) + escape(french) + match.group(3)

    return STRINGS.sub(strings, TEXT.sub(text, raw))


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--lang", default="fr", help="the language, the name of its lang/<language>.json")
    parser.add_argument("--out", help="the folder to write the translated files to (as <out>/ce/<file>.xml)")
    parser.add_argument("--check", action="store_true", help="only list what the translation lacks")
    args = parser.parse_args()
    table = json.loads((MENUS / "lang" / f"{args.lang}.json").read_text(encoding="utf-8"))
    table.setdefault("text", {})
    table.setdefault("strings", {})

    seen, missing, written = set(), set(), 0
    for path in sorted((MENUS / FOLDER).glob("*.xml")):
        raw = path.read_bytes().decode("utf-8")
        translated = translate_file(raw, table, seen, missing)
        if translated == raw:
            continue
        if structure(raw.encode("utf-8")) != structure(translated.encode("utf-8")):
            raise SystemExit(f"{path.name}: the translation changed more than the texts")
        if args.out and not args.check:
            target = Path(args.out) / FOLDER / path.name
            target.parent.mkdir(parents=True, exist_ok=True)
            with open(target, "w", encoding="utf-8", newline="") as out:
                out.write(translated)
        written += 1

    pictures = Path(__file__).resolve().parent.parent / "port/assets/menus/lang" / args.lang / FOLDER
    if args.out and not args.check and pictures.is_dir():
        # the language's pictures (tools/translate_menu_art.py) go beside the files
        for picture in pictures.rglob("*"):
            if picture.is_file():
                target = Path(args.out) / FOLDER / picture.relative_to(pictures)
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(picture.read_bytes())

    unused = [(kind, english) for kind in ("text", "strings") for english in table[kind]
              if (kind, english) not in seen]
    print(f"{args.lang}: {written} files translated, {len(missing)} texts left in English, "
          f"{len(unused)} translations no menu has", file=sys.stderr)
    if args.check:
        for english in sorted(missing):
            print(f"missing: {english!r}")
        for kind, english in unused:
            print(f"unused {kind}: {english!r}")
    elif not args.out:
        parser.error("--out is needed (or --check)")


if __name__ == "__main__":
    main()
