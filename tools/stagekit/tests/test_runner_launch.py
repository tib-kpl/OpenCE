"""Each scenario must execute its own inode, even while another copy is open for writing."""

import concurrent.futures
import shutil
import sys

import pytest

import runner


@pytest.mark.skipif(sys.platform != "linux", reason="Linux executable write exclusion")
def test_parallel_launches_do_not_share_the_source_executable(tmp_path, monkeypatch):
    source = tmp_path / "halo"
    shutil.copy("/bin/true", source)
    monkeypatch.setattr(runner, "GAME", source)

    def launch(number):
        machine = tmp_path / str(number)
        machine.mkdir()
        process = runner.launch(machine, "", "127.0.0.1", "127.0.0.2", "log")
        try:
            assert process.wait(timeout=10) == 0
            assert not (machine / "halo").samefile(source)
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()

    # A hard link carries the source's write-open state into every scenario.
    # Keep it open without changing its contents to reproduce ETXTBSY reliably.
    with source.open("r+b"), concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(launch, range(4)))
