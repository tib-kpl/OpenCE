"""Exercise the real Windows pthread shim's handle lifetime and join result."""
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.harness import function, structure


def run():
    if sys.platform != "win32":
        print("Windows thread checks require Windows")
        return
    cc = shutil.which("clang")
    if not cc:
        raise RuntimeError("clang required")
    original = (ROOT / "port/windows/src/win32_posix.c").read_text(encoding="utf-8")
    code = '#include <windows.h>\n#include <errno.h>\n#include <stdlib.h>\n#include <assert.h>\n#include "pthread.h"\n'
    code += structure(original, "thread_start") + '\n' + structure(original, "joinable_thread")
    code += '\nstatic SRWLOCK joinable_lock = SRWLOCK_INIT;\nstatic struct joinable_thread *joinable_threads;\n'
    code += '\n'.join(function(original, name) for name in (
        "take_joinable_thread", "thread_main", "pthread_create", "pthread_detach", "pthread_join",
        "pthread_self", "pthread_equal", "pthread_attr_init", "pthread_attr_setdetachstate"))
    code += r'''
static HANDLE started, release_worker;
static void *worker(void *value) {
    SetEvent(started);
    assert(WaitForSingleObject(release_worker,5000)==WAIT_OBJECT_0);
    return value;
}
static void *quick(void *value) { return value; }
static void *detach_self(void *unused) {
    assert(!pthread_detach(pthread_self())); SetEvent(started); return NULL;
}
int main(void) {
    pthread_t thread; pthread_attr_t attr; void *result=NULL;
    DWORD before, after; int i;
    started=CreateEvent(NULL,FALSE,FALSE,NULL);
    release_worker=CreateEvent(NULL,FALSE,FALSE,NULL);
    assert(started && release_worker);
    assert(GetProcessHandleCount(GetCurrentProcess(),&before));
    assert(!pthread_create(&thread,NULL,worker,(void *)0x123456));
    assert(!pthread_equal(thread,pthread_self()));
    assert(WaitForSingleObject(started,5000)==WAIT_OBJECT_0);
    SetEvent(release_worker);
    assert(!pthread_join(thread,&result) && result==(void *)0x123456);
    assert(pthread_join(thread,NULL)==ESRCH);
    assert(pthread_join(pthread_self(),NULL)==EDEADLK);
    /* Joining remains valid after the callback has already returned. */
    assert(!pthread_create(&thread,NULL,quick,(void *)0x9876)); Sleep(30);
    assert(!pthread_join(thread,&result) && result==(void *)0x9876);
    for(i=0;i<100;i++) {
        assert(!pthread_create(&thread,NULL,quick,NULL));
        assert(!pthread_join(thread,NULL));
    }
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr,PTHREAD_CREATE_DETACHED);
    assert(!pthread_create(&thread,&attr,worker,NULL));
    assert(WaitForSingleObject(started,5000)==WAIT_OBJECT_0);
    assert(pthread_join(thread,NULL)==ESRCH); SetEvent(release_worker);
    assert(!pthread_create(&thread,NULL,detach_self,NULL));
    assert(WaitForSingleObject(started,5000)==WAIT_OBJECT_0);
    assert(pthread_join(thread,NULL)==ESRCH);
    Sleep(30);
    assert(GetProcessHandleCount(GetCurrentProcess(),&after));
    assert(after<=before);
    CloseHandle(started); CloseHandle(release_worker); return 0;
}
'''
    with tempfile.TemporaryDirectory(prefix="halo-windows-threads-") as folder:
        path = Path(folder) / "threads.c"
        binary = Path(folder) / "threads.exe"
        path.write_text(code, encoding="utf-8")
        subprocess.run([cc, "--target=i686-pc-windows-msvc", "-fuse-ld=lld", "-std=gnu11",
                        f"-I{ROOT / 'port/windows/include/posix'}", str(path), "-o", str(binary)], check=True)
        subprocess.run([str(binary)], check=True, timeout=30)
    print("Windows thread join, detach, result and handle lifetime checks passed")


if __name__ == "__main__":
    run()
