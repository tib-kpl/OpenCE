"""Version-41 advertisements join; genuinely incompatible hosts stay refused."""
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from harness import CHECK_FAILED, build, constant, function, mutated, read, run


def generated(fault=None):
    limits = read('port/linux/include/halo_port_limits.h')
    version = '\n'.join(f'#define {name} {constant(limits, name)}' for name in (
        'HALO_PORT_NETWORK_VERSION', 'HALO_PORT_ADVERTISED_DISTRIBUTED_FLAG'))
    code = function(read('source/networking/network_client_manager.c'), 'network_game_client_advertised_game_compatible')
    if fault:
        code = mutated(code, *fault)
    return (('version.inc', version), ('under_test.inc', code))


@pytest.mark.parametrize('case', ['compatible41', 'older', 'newer', 'lockstep', 'silent', 'invalid'])
def test_join_gate(case):
    status, output = run(build('network_compatibility', generated()), case)
    assert status == 0, output


@pytest.mark.parametrize('fault,case', [
    (('theirs == ours && distributed', 'distributed'), 'newer'),
    (('theirs == ours && distributed', 'theirs == ours'), 'lockstep'),
    (('unsigned int ours = HALO_PORT_NETWORK_VERSION;', 'unsigned int ours = 42;'), 'compatible41'),
])
def test_negative_control(fault, case):
    status, output = run(build('network_compatibility', generated(fault)), case)
    assert status == CHECK_FAILED, output
