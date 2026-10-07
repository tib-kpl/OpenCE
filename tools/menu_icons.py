#!/usr/bin/env python3
"""Draws the menu pictures that have no redraw (the previews on the right of the Multiplayer and
Settings menus), as plain illustrations in the menus' blue, and renders them to the PNGs the menus use:

    python tools/menu_icons.py --rsvg "wsl.exe -d Ubuntu -u root -- rsvg-convert"

Each is a 512x256 picture with a transparent background (as the PC version's are). They stand in
for the PC version's pictures (Bungie's art: not shipped), one per entry of the menu list it
previews: see port/assets/menus/NON_HANDDRAWN.md.

The menus draw a picture texel for texel from its top left corner, as much of it as the widget
showing it covers (ui_widget.c): only that corner of the 512x256 is seen. So each drawing is drawn
once, measured (what it covers, its glow too), and fitted and centred in the corner its menu shows.
"""

import argparse
import shlex
import subprocess
import tempfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
CE = ROOT / "port/assets/menus/ce/shell/main_menu"
SVG_OUT = ROOT / "port/assets/menus/svg_icons"

BLUE = "#2896ff"
DEFS = f"""<defs>
  <linearGradient id="metal" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#f1f1f1"/><stop offset="1" stop-color="#b9bcc2"/>
  </linearGradient>
  <linearGradient id="screen" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#eef3fa"/><stop offset="1" stop-color="#cfd9e8"/>
  </linearGradient>
  <filter id="glow" x="-20%" y="-20%" width="140%" height="140%">
    <feGaussianBlur in="SourceAlpha" stdDeviation="4" result="b"/>
    <feFlood flood-color="{BLUE}" flood-opacity="0.9"/>
    <feComposite in2="b" operator="in" result="g"/>
    <feMerge><feMergeNode in="g"/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>
</defs>"""
def out(width=4):
    return f'stroke="{BLUE}" stroke-width="{width}" stroke-linejoin="round" stroke-linecap="round"'


OUT = out()
RED = "#e0535a"
VIOLET = "#6a6fe0"


def svg(body):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="256" viewBox="0 0 512 256">'
            f'{DEFS}<g filter="url(#glow)">{body}</g></svg>')


def monitor(x, y, w, h, screen_extra=""):
    sx, sy = x + 14, y + 14
    sw, sh = w - 28, h - 28
    cx = x + w / 2
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="10" fill="url(#metal)" {OUT}/>'
            f'<rect x="{sx}" y="{sy}" width="{sw}" height="{sh}" rx="3" fill="url(#screen)" stroke="#8aa3c4" stroke-width="2"/>'
            f'{screen_extra}'
            f'<rect x="{cx - 18}" y="{y + h}" width="36" height="16" fill="url(#metal)" {OUT}/>'
            f'<path d="M{cx - 60},{y + h + 36} Q{cx},{y + h + 8} {cx + 60},{y + h + 36} L{cx + 60},{y + h + 46} '
            f'Q{cx},{y + h + 56} {cx - 60},{y + h + 46} Z" fill="url(#metal)" {OUT}/>')


def soldier(x, y, color, flip=1):
    """a small standing figure with a rifle, centred on x, feet at y"""
    k = flip
    return (f'<g transform="translate({x},{y}) scale({k},1)">'
            f'<circle cx="0" cy="-118" r="15" fill="{color}" {OUT}/>'
            f'<path d="M-24,-98 L24,-98 L20,-48 L-20,-48 Z" fill="{color}" {OUT}/>'
            f'<path d="M-18,-48 L-22,0 M18,-48 L22,0" fill="none" stroke="{color}" stroke-width="14" stroke-linecap="round"/>'
            f'<path d="M-6,-86 L52,-96 L52,-84 L-6,-72 Z" fill="#3a3f4a" {OUT}/></g>')


def versus():
    flag = (f'<path d="M256,40 L256,200" stroke="{BLUE}" stroke-width="5" fill="none" stroke-linecap="round"/>'
            f'<circle cx="256" cy="38" r="6" fill="#fff" {OUT}/>'
            f'<path d="M258,50 L330,56 L316,82 L330,108 L258,102 Z" fill="{RED}" {OUT}/>')
    return svg(soldier(150, 218, RED) + flag + soldier(362, 218, VIOLET, -1))


def server_list():
    bar = ('<rect x="176" y="118" width="160" height="12" rx="2" fill="#fff" stroke="#8aa3c4" stroke-width="2"/>'
           '<rect x="178" y="120" width="70" height="8" fill="#6aa8ff"/>')
    rows = ''.join(f'<rect x="178" y="{58 + i * 16}" width="156" height="9" rx="2" fill="#9db6d8"/>' for i in range(3))
    return svg(monitor(156, 30, 200, 150, rows + bar))


def tag():  # change name
    return svg(f'<rect x="126" y="76" width="260" height="84" rx="12" fill="url(#metal)" {OUT}/>'
               f'<rect x="150" y="98" width="150" height="14" rx="4" fill="#3a3f4a"/>'
               f'<rect x="150" y="124" width="96" height="14" rx="4" fill="#3a3f4a"/>'
               f'<rect x="322" y="96" width="8" height="46" fill="{BLUE}"/>')


def crate():  # item options
    return svg(f'<path d="M176,94 L256,66 L336,94 L336,176 L256,206 L176,176 Z" fill="url(#metal)" {OUT}/>'
               f'<path d="M176,94 L256,122 L336,94 M256,122 L256,206" fill="none" {OUT}/>'
               f'<path d="M216,80 L296,108 M296,80 L216,108" fill="none" stroke="#8aa3c4" stroke-width="3"/>'
               f'<rect x="240" y="40" width="32" height="14" rx="3" fill="{RED}" {OUT}/>')


def radar():  # indicator options
    rings = ''.join(f'<circle cx="256" cy="128" r="{r}" fill="none" stroke="#8aa3c4" stroke-width="2"/>' for r in (30, 60))
    return svg(f'<circle cx="256" cy="128" r="92" fill="url(#screen)" {OUT}/>{rings}'
               f'<path d="M256,128 L256,40 M256,128 L330,166" stroke="#8aa3c4" stroke-width="2" fill="none"/>'
               f'<path d="M256,112 L268,138 L256,132 L244,138 Z" fill="{BLUE}" {out(2)}/>'
               f'<circle cx="300" cy="92" r="8" fill="{RED}"/><circle cx="214" cy="150" r="8" fill="{RED}"/>'
               f'<circle cx="286" cy="164" r="8" fill="#ffd54a"/>')


def jeep():  # vehicle options
    return svg(f'<path d="M110,150 L130,112 L212,112 L240,84 L330,84 L352,112 L408,118 L412,150 Z" fill="url(#metal)" {OUT}/>'
               f'<path d="M246,92 L324,92 L340,112 L228,112 Z" fill="url(#screen)" stroke="#8aa3c4" stroke-width="2"/>'
               f'<path d="M170,112 L170,86 L206,86" fill="none" {OUT}/>'
               f'<circle cx="164" cy="160" r="30" fill="#3a3f4a" {OUT}/><circle cx="164" cy="160" r="12" fill="#cfd4dc"/>'
               f'<circle cx="360" cy="160" r="30" fill="#3a3f4a" {OUT}/><circle cx="360" cy="160" r="12" fill="#cfd4dc"/>')


def teams():  # teamplay options
    def person(x, color):
        return (f'<circle cx="{x}" cy="104" r="16" fill="{color}" {OUT}/>'
                f'<path d="M{x - 30},170 Q{x - 30},128 {x},128 Q{x + 30},128 {x + 30},170 Z" fill="{color}" {OUT}/>')
    return (svg(person(150, RED) + person(206, RED) + person(306, VIOLET) + person(362, VIOLET)
                + f'<path d="M256,70 L256,190" stroke="{BLUE}" stroke-width="3" fill="none" stroke-dasharray="8 8"/>'))


def keyboard():
    keys = ''
    for row, (n, x0, y) in enumerate(((10, 138, 98), (9, 152, 130), (8, 166, 162))):
        for i in range(n):
            keys += f'<rect x="{x0 + i * 24}" y="{y}" width="19" height="22" rx="4" fill="#fff" stroke="#8aa3c4" stroke-width="2"/>'
    return svg(f'<rect x="116" y="76" width="280" height="130" rx="14" fill="url(#metal)" {OUT}/>{keys}'
               f'<rect x="198" y="188" width="116" height="12" rx="4" fill="#fff" stroke="#8aa3c4" stroke-width="2"/>')


def mouse():
    return svg(f'<path d="M256,40 C206,40 186,76 186,120 L186,160 C186,196 216,218 256,218 C296,218 326,196 326,160 L326,120 C326,76 306,40 256,40 Z" fill="url(#metal)" {OUT}/>'
               f'<path d="M186,112 L326,112 M256,40 L256,112" fill="none" {out(3)}/>'
               f'<rect x="246" y="62" width="20" height="34" rx="8" fill="{BLUE}"/>')


def speaker():
    waves = ''.join(f'<path d="M{300 + i * 24},{102 - i * 10} Q{326 + i * 24},128 {300 + i * 24},{154 + i * 10}" fill="none" {OUT}/>'
                    for i in range(3))
    return svg(f'<path d="M150,108 L196,108 L252,64 L252,192 L196,148 L150,148 Z" fill="url(#metal)" {OUT}/>{waves}')


def screen_icon():  # video setup
    scene = ('<path d="M172,150 L214,104 L246,134 L274,108 L340,150 Z" fill="#7fae7a" stroke="#5c8a58" stroke-width="2"/>'
             '<circle cx="312" cy="82" r="12" fill="#ffd54a"/>')
    return svg(monitor(156, 30, 200, 150, scene))


def palette():  # change colour
    colours = (RED, "#ff9d3c", "#ffd54a", "#5fd068", "#46b6e8", VIOLET)
    swatches = ''.join(f'<rect x="{138 + i * 40}" y="102" width="34" height="52" rx="5" fill="{c}" stroke="#fff" stroke-width="3"/>'
                       for i, c in enumerate(colours))
    return svg(f'<rect x="124" y="80" width="268" height="96" rx="12" fill="url(#metal)" {OUT}/>{swatches}')


def network():
    def pc(x, y):
        return (f'<rect x="{x}" y="{y}" width="70" height="50" rx="6" fill="url(#metal)" {OUT}/>'
                f'<rect x="{x + 8}" y="{y + 8}" width="54" height="30" fill="url(#screen)" stroke="#8aa3c4" stroke-width="2"/>'
                f'<rect x="{x + 18}" y="{y + 56}" width="34" height="8" fill="url(#metal)" {OUT}/>')
    return svg(f'<path d="M256,100 L146,140 M256,100 L366,140 M146,140 L366,140" fill="none" {OUT}/>'
               f'<circle cx="256" cy="86" r="26" fill="url(#screen)" {OUT}/>'
               f'<path d="M244,86 L268,86 M256,74 C250,80 250,92 256,98 C262,92 262,80 256,74" fill="none" stroke="#5a84b8" stroke-width="3"/>'
               + pc(80, 130) + pc(330, 130))


# the part of the picture each menu shows: its widget's size (port/assets/menus/ce/*.xml), from the
# top left corner
SHOWN = {
    "multiplayer_type_select": (307, 244),  # multiplayer_options_pic
    "settings_select/multiplayer_setup/playlist_edit": (284, 208),  # playlist_edit_ext_desc_pic
    "settings_select/player_setup/player_profile_edit": (279, 202),  # profile_edit_extended_desc_pic
}
MARGIN = 10


def fitted(drawing, covered, shown):
    """the drawing, the part it covers (x0, y0, x1, y1) fitted and centred in shown (width, height)"""
    x0, y0, x1, y1 = covered
    width, height = shown
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="256" viewBox="0 0 512 256">'
            f'<svg x="{MARGIN}" y="{MARGIN}" width="{width - 2 * MARGIN}" height="{height - 2 * MARGIN}" '
            f'viewBox="{x0} {y0} {x1 - x0} {y1 - y0}">{drawing}</svg></svg>')


# (file below ce/shell/main_menu, picture)
PICTURES = {
    "multiplayer_type_select/mp_options__0": versus,
    "multiplayer_type_select/mp_options__1": server_list,
    "settings_select/multiplayer_setup/playlist_edit/gametype_options__0": tag,
    "settings_select/multiplayer_setup/playlist_edit/gametype_options__3": crate,
    "settings_select/multiplayer_setup/playlist_edit/gametype_options__4": radar,
    "settings_select/multiplayer_setup/playlist_edit/gametype_options__6": jeep,
    "settings_select/multiplayer_setup/playlist_edit/gametype_options__7": teams,
    "settings_select/player_setup/player_profile_edit/profile_options__1": keyboard,
    "settings_select/player_setup/player_profile_edit/profile_options__3": mouse,
    "settings_select/player_setup/player_profile_edit/profile_options__4": speaker,
    "settings_select/player_setup/player_profile_edit/profile_options__5": screen_icon,
    "settings_select/player_setup/player_profile_edit/profile_options__6": palette,
    "settings_select/player_setup/player_profile_edit/profile_options__8": network,
}


def windows_to_wsl(path):
    path = str(path)
    return "/mnt/" + path[0].lower() + path[2:].replace("\\", "/") if len(path) > 2 and path[1] == ":" else path


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--rsvg", default="rsvg-convert")
    args = parser.parse_args()
    command = shlex.split(args.rsvg, posix=False)
    wsl = Path(command[0]).stem.lower() == "wsl"
    def render(svg_path, png):
        subprocess.run(command + ["-o", windows_to_wsl(png) if wsl else str(png),
                                  windows_to_wsl(svg_path) if wsl else str(svg_path)], check=True)

    for name, draw in PICTURES.items():
        svg_path = SVG_OUT / (name + ".svg")
        svg_path.parent.mkdir(parents=True, exist_ok=True)
        png = CE / (name + ".png")
        if not png.parent.is_dir():
            raise SystemExit(f"{png.parent} does not exist")
        shown = SHOWN[name.rsplit("/", 1)[0]]
        # drawn once as it is, to measure what it covers
        svg_path.write_text(draw(), encoding="utf-8", newline="\n")
        with tempfile.TemporaryDirectory(dir=SVG_OUT) as folder:
            measured = Path(folder) / "measured.png"
            render(svg_path, measured)
            with Image.open(measured) as image:
                covered = image.getchannel("A").point(lambda alpha: 255 if alpha > 8 else 0).getbbox()
        svg_path.write_text(fitted(draw(), covered, shown), encoding="utf-8", newline="\n")
        render(svg_path, png)
        print(name, "covers", covered, "shown in", shown)


if __name__ == "__main__":
    main()
