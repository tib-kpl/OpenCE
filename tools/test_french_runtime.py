"""Exercise the production overlay/replay text and UTF-8 panel wrapping."""
from pathlib import Path
import shutil
import subprocess

import pytest

from tools.harness import function

ROOT = Path(__file__).resolve().parents[1]
GAME = ROOT / "port/linux/game"


@pytest.fixture(scope="module")
def text_program(tmp_path_factory):
    cc = shutil.which("clang")
    if not cc:
        pytest.skip("clang required")
    overlay = (GAME / "overlay_menu.c").read_text(encoding="utf-8")
    replay = (GAME / "network_replay_playback.c").read_text(encoding="utf-8")
    menus = (GAME / "menu_functions.c").read_text(encoding="utf-8")
    source = r'''
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <stddef.h>
#define NUMBEROF(a) (sizeof(a)/sizeof((a)[0]))
#define MAX(a,b) ((a)>(b)?(a):(b))
#define NONE -1
#define FALSE 0
#define TRUE 1
#define THEATER_TEXT_LENGTH 512
#define THEATER_LINE_CHARACTERS 30
#define MOD_TEXT_MARGIN 0
typedef int boolean;
typedef struct { float a,r,g,b; } real_argb_color;
typedef struct { short x0,y0,x1,y1; } rectangle2d;
static int french=1;
static int port_french(void) { return french; }
static struct { long details_font; short details_width; } theater;
static void draw_string_set_draw_mode(long f,int a,int b,int c,const real_argb_color *d) {}
/* A proportional fake font: a wide W and narrow i exercise measured widths. */
static void draw_unicode_string_compute_bounds(rectangle2d *b,const wchar_t *s,rectangle2d *m,rectangle2d *c) {
    short width=0;
    while (*s) { width += *s=='W' ? 12 : *s=='i' ? 2 : 6; s++; }
    memset(m,0,sizeof(*m)); memset(c,0,sizeof(*c)); m->x1=width; c->x0=width;
}
'''
    for code, names in [(overlay, ("overlay_localized", "overlay_unicode", "overlay_capitals")),
                        (replay, ("playback_words",)),
                        (menus, ("mods_wide_text", "mods_character_before", "menu_text_fits", "theater_append_wrapped"))]:
        source += "\n" + "\n".join(function(code, name) for name in names)
    source += r'''
int main(int argc,char **argv) {
    char out[512]; wchar_t wide[16];
    switch(atoi(argv[1])) {
    case 0:
        assert(!strcmp(overlay_localized("God Mode"), "Invincibilité"));
        assert(!strcmp(overlay_localized("My custom folder"), "My custom folder"));
        overlay_capitals(out,sizeof(out),"Vehicles");
        assert(!strcmp(out,"VÉHICULES"));
        overlay_capitals(out,3,"Été"); assert(!strcmp(out,"É"));
        overlay_capitals(out,2,"Été"); assert(!out[0]);
        assert(playback_words(L"FREE CAMERA")[3]==0xC9);
        french=0;
        assert(!strcmp(overlay_localized("God Mode"), "God Mode"));
        assert(playback_words(L"FREE CAMERA")[0]=='F');
        break;
    case 1:
        overlay_unicode("Été ’",wide,16);
        assert(wide[0]==0xC9 && wide[2]==0xE9 && wide[4]==0x2019 && !wide[5]);
        overlay_unicode("éé",wide,2); assert(wide[0]==0xE9 && !wide[1]);
        overlay_unicode("\xF0\x9F\x98\x80",wide,16); assert(wide[0]=='?' && !wide[1]);
        overlay_unicode("\xC3",wide,16); assert(wide[0]=='?' && !wide[1]);
        wide[0]=123; overlay_unicode("a",wide,0); assert(wide[0]==123);
        break;
    case 2:
        theater.details_width=24;
        out[0]=0; theater_append_wrapped(out,sizeof(out),"WWiiiiiiiiéé");
        puts(out);
        break;
    case 3:
        theater.details_width=24;
        out[0]=0; theater_append_wrapped(out,4,"ééé");
        puts(out);
        break;
    }
    return 0;
}
'''
    folder = tmp_path_factory.mktemp("french-runtime")
    path = folder / "text.c"
    path.write_text(source, encoding="utf-8")
    binary = folder / "text"
    subprocess.run([cc, "-m32", "-fshort-wchar", "-std=gnu89", "-I", str(GAME), str(path), "-o", str(binary)], check=True)
    return binary


@pytest.mark.parametrize("case", range(4))
def test_text(text_program, case):
    output = subprocess.run([str(text_program), str(case)], capture_output=True, check=True).stdout.decode("utf-8")
    if case == 2:
        assert output.strip().replace("\n", "") == "WWiiiiiiiiéé"
        assert all(sum(12 if c == "W" else 2 if c == "i" else 6 for c in line) <= 24 for line in output.splitlines())
    if case == 3:
        assert output == "é\n"
