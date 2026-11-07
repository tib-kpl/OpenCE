"""Actual stick rate code: independent axes, stock compatibility and local profiles."""
from pathlib import Path
import shutil
import subprocess

import pytest

from tools.harness import function

ROOT = Path(__file__).resolve().parents[1]


@pytest.fixture(scope="module")
def rates(tmp_path_factory):
    cc = shutil.which("clang")
    if not cc:
        pytest.skip("clang required")
    folder = tmp_path_factory.mktemp("stick-look")
    source = folder / "rates.c"
    source.write_text(r'''
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
static double horizontal[4], vertical[4];
static int equal[4];
static double config_player_real(int p, const char *key) {
    return strstr(key, "horizontal") ? horizontal[p] : vertical[p];
}
static int config_player_boolean(int p, const char *key) { return equal[p]; }
''' + function((ROOT / "port/linux/game/stick_look.c").read_text(), "port_stick_look_rates") + r'''
int main(int argc, char **argv) {
    int p = atoi(argv[1]), i;
    float yaw = atof(argv[5]), pitch = atof(argv[6]);
    for(i=0;i<4;i++) { horizontal[i]=10; vertical[i]=10; equal[i]=1; }
    horizontal[p]=atof(argv[2]); vertical[p]=atof(argv[3]); equal[p]=atoi(argv[4]);
    port_stick_look_rates(p, &yaw, &pitch);
    printf("%g %g\n", yaw, pitch);
    return 0;
}
''', encoding="utf-8")
    binary = folder / "rates"
    subprocess.run([cc, "-m32", "-std=gnu89", "-Werror=implicit-function-declaration", str(source), "-o", str(binary)], check=True)
    return binary


@pytest.mark.parametrize("player", range(4))
@pytest.mark.parametrize("horizontal,vertical,equal,base,expected", [
    (0, 0, 0, (120, 60), (120, 60)),
    (0, 0, 1, (120, 60), (120, 120)),
    (2, 0, 0, (120, 60), (100, 60)),
    (0, 5, 0, (120, 60), (120, 80)),
    (3, 3, 1, (260, 130), (120, 120)),
    (2, 5, 1, (120, 60), (100, 160)),
    (1000, .25, 0, (120, 60), (260, 40)),
    (-1, "nan", 0, (140, 70), (140, 70)),
])
def test_rates(rates, player, horizontal, vertical, equal, base, expected):
    result = subprocess.run([str(rates), *map(str, (player, horizontal, vertical, equal, *base))], capture_output=True, text=True, check=True)
    assert tuple(map(float, result.stdout.split())) == pytest.approx(expected)
