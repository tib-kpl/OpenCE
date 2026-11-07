"""Real replay storage across chunks and unsupported-layout snapshot safety."""
from pathlib import Path
import shutil
import subprocess

import pytest

from tools.harness import function

ROOT = Path(__file__).resolve().parents[1]


def test_native_replay_store(tmp_path):
    cc = shutil.which("clang")
    if not cc:
        pytest.skip("clang required")
    path = tmp_path / "store.c"
    path.write_text(r'''
#include <assert.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
int host_replay_store_append(uint32_t data,uint32_t size);
uint32_t host_replay_store_read(uint32_t offset,uint32_t destination,uint32_t size);
void host_replay_store_free(void);
int main(void) {
    const uint32_t chunk=4u*1024*1024;
    unsigned char *data=malloc(chunk+31), out[64];
    uint32_t i;
    assert(data);
    for(i=0;i<chunk+31;i++)data[i]=(unsigned char)(i*37);
    assert(host_replay_store_append((uint32_t)(uintptr_t)data,chunk-7));
    assert(host_replay_store_append((uint32_t)(uintptr_t)(data+chunk-7),38));
    assert(host_replay_store_read(chunk-16,(uint32_t)(uintptr_t)out,64)==47);
    assert(!memcmp(out,data+chunk-16,47));
    assert(!host_replay_store_read(UINT32_MAX,(uint32_t)(uintptr_t)out,1));
    host_replay_store_free();
    assert(!host_replay_store_read(0,(uint32_t)(uintptr_t)out,1));
    assert(host_replay_store_append((uint32_t)(uintptr_t)data,3));
    assert(host_replay_store_read(0,(uint32_t)(uintptr_t)out,64)==3);
    assert(!memcmp(out,data,3));
    host_replay_store_free(); free(data); return 0;
}
''', encoding="utf-8")
    binary = tmp_path / "store"
    subprocess.run([cc, "-m32", "-std=gnu89", str(path), str(ROOT / "port/android/host/host_replay.c"), "-o", str(binary)], check=True)
    subprocess.run([str(binary)], check=True)


def test_native_layout_refuses_partial_snapshots(tmp_path):
    cc = shutil.which("clang")
    if not cc:
        pytest.skip("clang required")
    source = (ROOT / "port/linux/game/network_replay_playback.c").read_text(encoding="utf-8")
    path = tmp_path / "regions.c"
    path.write_text('#include <assert.h>\ntypedef unsigned char byte;\n' + function(source, "playback_keyframe_regions") + r'''
int main(void) {
    byte *regions[4]={(byte *)1,(byte *)2,(byte *)3,(byte *)4};
    unsigned long sizes[4]={11,12,13,14};
    assert(playback_keyframe_regions(regions,sizes)==0);
    assert(regions[0]==(byte *)1 && sizes[0]==11);
    return 0;
}
''', encoding="utf-8")
    binary = tmp_path / "regions"
    subprocess.run([cc, "-m32", "-std=gnu89", str(path), "-o", str(binary)], check=True)
    subprocess.run([str(binary)], check=True)
