"""Vulkan's retired visibility counts keep the test that produced them."""
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from harness import CHECK_FAILED, build, function, mutated, read, run


def generated(fault=None):
    source = read('port/android/host/host_vk_visibility.c')
    code = function(source, 'host_vk_visibility_retired')
    if fault:
        code = mutated(code, *fault)
    return (('under_test.inc', code),)


@pytest.mark.parametrize('case', ['reused', 'zero', 'scaled'])
def test_case(case):
    status, output = run(build('vk_visibility', generated()), case)
    assert status == 0, output


@pytest.mark.parametrize('fault,case', [
    (('(uint64_t)test->number << 32', '(uint64_t)0 << 32'), 'reused'),
    (('__atomic_store_n(&V.latest[test->index],', 'if (samples) __atomic_store_n(&V.latest[test->index],'), 'zero'),
    (('samples / test->area', 'samples * test->area'), 'scaled'),
])
def test_negative_control(fault, case):
    status, output = run(build('vk_visibility', generated(fault)), case)
    assert status == CHECK_FAILED, output
