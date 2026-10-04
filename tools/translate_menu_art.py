#!/usr/bin/env python3
"""Draws a language's copy of the PC menus' lettered pictures (the main menu's
items, the screens' headers: port/assets/menus/svg), from the texts of
port/assets/menus/lang/<language>.json ("art", by the picture's name):

    python tools/translate_menu_art.py --lang fr

The pictures' letters are paths, one definition for each letter and a <use>
for each letter drawn, placed by hand to the original typeface's spacing.
This lays a translation out with the same letters:

- the distance between two letters is the one the English pictures have for
  the pair, else one fitted to them (each letter has a space on each side, as
  the pairs of the English pictures show); a space is the English pictures'
  space;
- the items are centred, as the English ones are, the headers start where the
  English ones do for their first letter, and the header's dark backdrop
  (the blurred box under the text) follows the text's width;
- a text too wide for its picture is drawn smaller;
- the letters the English pictures lack are made from the ones they have: J
  (a U with the top of its left side cut), and the acute and grave accents of
  É, È and À (a slanted bar over the letter).

It writes the French SVGs to lang/<language>/svg and renders them to
lang/<language>/ce/shell, with the pictures' own names, as rsvg-convert does
for the English ones (-z 4): tools/translate_menus.py puts them beside the
translated XML files. Needs rsvg-convert (--rsvg takes its command, for
example "wsl.exe -d Ubuntu -u root -- rsvg-convert" on Windows).
"""

import argparse
import collections
import json
import re
import shlex
import statistics
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MENUS = ROOT / "port/assets/menus"

USE = re.compile(r'<use href="#(g[^"]+)" transform="translate\(([-\d.]+),([-\d.]+)\)(?: scale\(([-\d.]+)\))?"/>')
GROUP = re.compile(r"(<g\b[^>]*>)((?:\s*<use\b[^>]*/>)+)(\s*</g>)")
GLYPH = re.compile(r'[ \t]*<path id="(g[^"]+)" d="([^"]*)"/>[ \t]*\n?')
TEXT_GROUP = re.compile(r'<g id="text"[^>]*>(.*?)</g>', re.S)
COMMENT = re.compile(r'<!-- *"([^"]*)"')
BACKDROP = re.compile(r'(<rect x=")([-\d.]+)(" y="[-\d.]+" width=")([-\d.]+)(" height="[-\d.]+" fill="#021931")')

SPECIAL = {":": "gcolon", "1": "gone", "2": "gtwo"}
ACCENTED = {"É": ("E", "gacute"), "È": ("E", "ggrave"), "À": ("A", "ggrave")}
# an accent (a slanted bar), centred on 0, over the 21.5 high capitals
ACCENTS = {
    "gacute": "M-2,-2.4L2.7,-2.4L5.3,-6.6L0.6,-6.6Z",
    "ggrave": "M2,-2.4L-2.7,-2.4L-5.3,-6.6L-0.6,-6.6Z",
}
CAPITAL_HEIGHT = 21.5
# where an item is centred, and how wide its text may be (a 256 wide picture)
ITEM_CENTER = 130.0
ITEM_WIDTH = 236.0
# a header's dark backdrop starts this far left of the text and ends this far
# right of it (the mean of the English headers')
BACKDROP_LEFT = -1.8
BACKDROP_RIGHT = 10.8
HEADER_RIGHT_MARGIN = 22.0


def glyph_id(char):
    return SPECIAL.get(char, "g" + char)


def char_of(gid):
    for char, other in SPECIAL.items():
        if other == gid:
            return char
    return gid[1:]


def bounding_box(d):
    """a path of M, L, C and Z (absolute): its box, the curves sampled"""
    tokens = re.findall(r"[MLCZ]|-?\d+(?:\.\d+)?", d)
    points, current, start, command, i = [], (0.0, 0.0), (0.0, 0.0), None, 0
    while i < len(tokens):
        token = tokens[i]
        if token in "MLCZ":
            command = token
            i += 1
            if token == "Z":
                current = start
            continue
        if command in ("M", "L"):
            current = (float(tokens[i]), float(tokens[i + 1]))
            if command == "M":
                start = current
            points.append(current)
            i += 2
        elif command == "C":
            p = [current] + [(float(tokens[i + k]), float(tokens[i + k + 1])) for k in (0, 2, 4)]
            for n in range(1, 11):
                u = n / 10
                w = ((1 - u) ** 3, 3 * u * (1 - u) ** 2, 3 * u * u * (1 - u), u ** 3)
                points.append((sum(q[0] * k for q, k in zip(p, w)), sum(q[1] * k for q, k in zip(p, w))))
            current = p[3]
            i += 6
        else:
            i += 1
    return (min(p[0] for p in points), min(p[1] for p in points),
            max(p[0] for p in points), max(p[1] for p in points))


class Typeface:
    """the letters the English pictures define, and how they are spaced"""

    def __init__(self, svg_folder):
        self.paths = {}
        self.pairs = collections.defaultdict(list)    # (a, b) -> gaps within a word
        self.across = []                              # (a, b, gap) across a space
        self.first_left = collections.defaultdict(list)
        self.files = []
        for path in sorted(svg_folder.glob("**/*.svg")):
            svg = path.read_text(encoding="utf-8")
            for gid, d in GLYPH.findall(svg):
                self.paths.setdefault(gid, d)
        # J: a U with the top of its left side cut
        u = self.paths["gU"]
        self.paths["gJ"] = u.replace("M0,0L4.65,0L4.65,14.31", "M0,11.2L4.65,11.2L4.65,14.31", 1)
        assert self.paths["gJ"] != u
        self.paths.update(ACCENTS)
        self.box = {gid: bounding_box(d) for gid, d in self.paths.items()}
        for path in sorted(svg_folder.glob("**/*.svg")):
            self._learn(path.read_text(encoding="utf-8"))
        self._fit()

    def _learn(self, svg):
        group, comment = TEXT_GROUP.search(svg), COMMENT.search(svg)
        if not group or not comment:
            return
        uses = [(g, float(x), float(y), float(s) if s else 1.0) for g, x, y, s in USE.findall(group.group(1))]
        text = comment.group(1)
        if not uses or [char_of(g) for g, *_ in uses] != [c for c in text if c != " "]:
            return
        width = float(re.search(r'<svg[^>]* width="(\d+)"', svg).group(1))
        if width <= 256:
            return  # an item: centred, nothing about where a header starts
        g0, x0, _, s0 = uses[0]
        self.first_left[char_of(g0)].append(x0 + self.box[g0][0] * s0)

    def _fit(self):
        self.pairs = collections.defaultdict(list)
        self.across = []
        for path in sorted(MENUS.joinpath("svg").glob("**/*.svg")):
            svg = path.read_text(encoding="utf-8")
            group, comment = TEXT_GROUP.search(svg), COMMENT.search(svg)
            if not group or not comment:
                continue
            uses = [(g, float(x), float(y), float(s) if s else 1.0) for g, x, y, s in USE.findall(group.group(1))]
            if [char_of(g) for g, *_ in uses] != [c for c in comment.group(1) if c != " "]:
                continue
            index, spaced, previous = 0, False, None
            for char in comment.group(1):
                if char == " ":
                    spaced = True
                    continue
                current = uses[index]
                index += 1
                if previous is not None:
                    (g1, x1, _, s1), (g2, x2, _, s2) = previous, current
                    gap = (x2 - x1) / s1 - self.box[g1][2] + self.box[g2][0]
                    if spaced:
                        self.across.append((g1, g2, gap))
                    else:
                        self.pairs[(g1, g2)].append(gap)
                previous, spaced = current, False
        # gap(a, b) = right(a) + left(b): each letter's space on its sides
        right = collections.defaultdict(lambda: 1.7)
        left = collections.defaultdict(lambda: 1.7)
        observed = [(a, b, g) for (a, b), gaps in self.pairs.items() for g in gaps]
        for _ in range(60):
            for letter in {a for a, _, _ in observed}:
                values = [g - left[b] for a, b, g in observed if a == letter]
                right[letter] = sum(values) / len(values)
            for letter in {b for _, b, _ in observed}:
                values = [g - right[a] for a, b, g in observed if b == letter]
                left[letter] = sum(values) / len(values)
        self.right, self.left = right, left
        extra = [g - right[a] - left[b] for a, b, g in self.across]
        self.space = statistics.mean(extra) if extra else 10.0
        self.exact = {pair: statistics.mean(gaps) for pair, gaps in self.pairs.items()}
        # J as U, accents have no spacing of their own
        self.right["gJ"], self.left["gJ"] = self.right["gU"], self.left["gU"]

    def gap(self, a, b, spaced):
        if not spaced and (a, b) in self.exact:
            return self.exact[(a, b)]
        return self.right[a] + self.left[b] + (self.space if spaced else 0.0)

    def header_left(self, char):
        values = self.first_left.get(char)
        if values:
            return statistics.mean(values)
        # letters the English headers do not start with: those of alike shape
        like = {"R": "P", "O": "C", "J": "U", "Q": "C", "B": "P", "H": "N", "K": "N", "U": "N", "W": "V", "Y": "T"}
        if char in like and like[char] in self.first_left:
            return statistics.mean(self.first_left[like[char]])
        return 30.0


def lay_out(typeface, text, scale):
    """the text's letters: (glyph, accent, origin x) at a scale, with the
    box of the whole, in the picture's units, starting from x = 0"""
    letters, spaced = [], False
    for char in text:
        if char == " ":
            spaced = True
            continue
        base, accent = ACCENTED.get(char, (char, None))
        gid = glyph_id(base)
        if gid not in typeface.paths:
            raise SystemExit(f"no letter {char!r} to draw {text!r} with")
        letters.append((gid, accent, spaced))
        spaced = False
    placed, x = [], 0.0
    for index, (gid, accent, spaced) in enumerate(letters):
        if index:
            previous = letters[index - 1][0]
            x += (typeface.box[previous][2] - typeface.box[gid][0] + typeface.gap(previous, gid, spaced)) * scale
        placed.append((gid, accent, x))
    first, last = placed[0], placed[-1]
    return placed, first[2] + typeface.box[first[0]][0] * scale, last[2] + typeface.box[last[0]][2] * scale


def render_svg(typeface, svg, french, name):
    width = float(re.search(r'<svg[^>]* width="(\d+)"', svg).group(1))
    group = TEXT_GROUP.search(svg)
    uses = [(g, float(x), float(y), float(s) if s else 1.0) for g, x, y, s in USE.findall(group.group(1))]
    _, x0, y0, scale0 = uses[0]
    item = width <= 256
    scale = scale0
    for _ in range(40):
        placed, left, right = lay_out(typeface, french, scale)
        text_width = right - left
        if item:
            fits = text_width <= ITEM_WIDTH
        else:
            start = typeface.header_left(french[0] if french[0] not in ACCENTED else ACCENTED[french[0]][0])
            fits = start + text_width <= width - HEADER_RIGHT_MARGIN
        if fits:
            break
        scale *= 0.97
    else:
        raise SystemExit(f"{name}: {french!r} does not fit")
    if item:
        shift = ITEM_CENTER - (left + right) / 2
    else:
        shift = start - left
    # keep the letters' middle where the English ones' is when they shrink
    y = y0 + CAPITAL_HEIGHT * (scale0 - scale) / 2

    def uses_for():
        out = []
        for gid, accent, x in placed:
            attrs = f"translate({x + shift:.2f},{y:.2f})" + (f" scale({scale:.4f})" if scale != 1.0 else "")
            out.append(f'    <use href="#{gid}" transform="{attrs}"/>')
            if accent:
                base = typeface.box[gid]
                cx = (base[0] + base[2]) / 2
                attrs = f"translate({x + shift + cx * scale:.2f},{y:.2f})" + (f" scale({scale:.4f})" if scale != 1.0 else "")
                out.append(f'    <use href="#{accent}" transform="{attrs}"/>')
        return "\n".join(out)

    body = uses_for()
    out = GROUP.sub(lambda m: m.group(1) + "\n" + body + m.group(3), svg)
    needed = sorted({gid for gid, accent, _ in placed} | {accent for _, accent, _ in placed if accent})
    paths = "".join(f'    <path id="{gid}" d="{typeface.paths[gid]}"/>\n' for gid in needed)
    out = GLYPH.sub("", out)
    out = out.replace("<defs>\n", "<defs>\n" + paths, 1)
    out = COMMENT.sub(lambda m: f'<!-- "{french}"', out, count=1)
    if not item:
        left_edge, right_edge = left + shift, right + shift
        box_x = left_edge + BACKDROP_LEFT
        box_width = right_edge + BACKDROP_RIGHT - box_x
        out = BACKDROP.sub(lambda m: f"{m.group(1)}{box_x:.2f}{m.group(3)}{box_width:.2f}{m.group(5)}", out, count=1)
    return out, scale


def windows_to_wsl(path):
    path = str(path)
    if len(path) > 2 and path[1] == ":":
        return "/mnt/" + path[0].lower() + path[2:].replace("\\", "/")
    return path


def render_png(command, svg, png):
    command = list(command)
    wsl = Path(command[0]).stem.lower() == "wsl"
    args = command + ["-z", "4", "-o", windows_to_wsl(png) if wsl else str(png),
                      windows_to_wsl(svg) if wsl else str(svg)]
    subprocess.run(args, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--lang", default="fr")
    parser.add_argument("--rsvg", default="rsvg-convert", help="the rsvg-convert command")
    parser.add_argument("--only", help="only the pictures whose name has this in it")
    parser.add_argument("--no-render", action="store_true", help="write the SVGs only")
    args = parser.parse_args()
    table = json.loads((MENUS / "lang" / f"{args.lang}.json").read_text(encoding="utf-8")).get("art", {})
    if not table:
        raise SystemExit(f"{args.lang}.json has no art")
    typeface = Typeface(MENUS / "svg")
    command = shlex.split(args.rsvg, posix=False)
    out_svg = MENUS / "lang" / args.lang / "svg"
    out_png = MENUS / "lang" / args.lang / "ce"
    done = 0
    for path in sorted((MENUS / "svg/shell").glob("**/*.svg")):
        name = re.sub(r"__\d+$", "", path.stem)
        if name not in table or (args.only and args.only not in name):
            continue
        svg = path.read_text(encoding="utf-8")
        if not TEXT_GROUP.search(svg):
            continue
        french, scale = render_svg(typeface, svg, table[name], path.name)
        relative = path.relative_to(MENUS / "svg")
        target_svg = out_svg / relative
        target_svg.parent.mkdir(parents=True, exist_ok=True)
        target_svg.write_text(french, encoding="utf-8", newline="\n")
        png = out_png / relative.with_suffix("")
        png = png.with_name(png.name + ("" if (MENUS / "ce" / relative.with_suffix(".png")).exists() else "__0") + ".png")
        if not args.no_render:
            png.parent.mkdir(parents=True, exist_ok=True)
            render_png(command, target_svg, png)
        done += 1
        print(f"{relative.as_posix():90s} {table[name]!r}" + (f" (scale {scale:.3f})" if scale < 0.999 and path.stem.endswith(("__0", "__1")) is False else ""))
    print(f"{done} pictures", file=sys.stderr)


if __name__ == "__main__":
    main()
