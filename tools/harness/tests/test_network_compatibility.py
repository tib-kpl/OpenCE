"""Versions 41 and the historical 42 join; other protocols stay refused."""
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from harness import CHECK_FAILED, build, constant, function, mutated, read, run


def generated(fault=None, protocol=None):
    limits = read('port/linux/include/halo_port_limits.h')
    version = '\n'.join(f'#define {name} {constant(limits, name)}' for name in (
        'HALO_PORT_NETWORK_VERSION', 'HALO_PORT_ADVERTISED_DISTRIBUTED_FLAG'))
    if protocol is not None:
        version = version.replace(f'#define HALO_PORT_NETWORK_VERSION {constant(limits, "HALO_PORT_NETWORK_VERSION")}',
                                  f'#define HALO_PORT_NETWORK_VERSION {protocol}')
    code = function(limits, 'halo_port_network_version_compatible') + '\n'
    code += function(read('source/networking/network_client_manager.c'), 'network_game_client_advertised_game_compatible')
    if fault:
        code = mutated(code, *fault)
    return (('version.inc', version), ('under_test.inc', code))


@pytest.mark.parametrize('case', ['compatible41', 'compatible42', 'older', 'newer', 'lockstep', 'silent', 'invalid'])
def test_join_gate(case):
    status, output = run(build('network_compatibility', generated()), case)
    assert status == 0, output


@pytest.mark.parametrize('fault,case', [
    (('halo_port_network_version_compatible(theirs) && distributed', 'distributed'), 'newer'),
    (('halo_port_network_version_compatible(theirs) && distributed', 'halo_port_network_version_compatible(theirs)'), 'lockstep'),
    (('HALO_PORT_NETWORK_VERSION == 41 && version == 42', '0'), 'compatible42'),
])
def test_negative_control(fault, case):
    status, output = run(build('network_compatibility', generated(fault)), case)
    assert status == CHECK_FAILED, output


def test_alias_does_not_follow_a_future_protocol_upgrade():
    status, output = run(build('network_compatibility', generated(protocol=43)), 'future_protocol')
    assert status == 0, output
    fault = ('HALO_PORT_NETWORK_VERSION == 41 && version == 42', 'version == 42')
    status, output = run(build('network_compatibility', generated(fault, protocol=43)), 'future_protocol')
    assert status == CHECK_FAILED, output
