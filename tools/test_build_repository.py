"""Build identity and the fork's updater must agree on their GitHub repository."""
from types import SimpleNamespace

import pytest

from tools import linux_build, windows_build


@pytest.mark.parametrize('builder', [linux_build, windows_build])
@pytest.mark.parametrize('release', [False, True])
@pytest.mark.parametrize('ci,remote,expected', [
    ('tib-kpl/OpenCE', 'https://github.com/OpenCommunityEdition/OpenCE', 'tib-kpl/OpenCE'),
    ('OpenCommunityEdition/OpenCE', '', 'OpenCommunityEdition/OpenCE'),
    ('', 'git@github.com:tib-kpl/OpenCE.git', 'tib-kpl/OpenCE'),
    ('', 'https://user:secret@github.com/tib-kpl/OpenCE.git', 'tib-kpl/OpenCE'),
    ('', 'https://example.com/owner/repo', ''),
    ('bad\"/value', 'https://github.com/tib-kpl/OpenCE', ''),
])
def test_identity_and_update_repository(monkeypatch, builder, release, ci, remote, expected):
    monkeypatch.setenv('GITHUB_REPOSITORY', ci)
    monkeypatch.setenv('HALO_BUILD_NUMBER', '87')
    monkeypatch.setattr(linux_build.subprocess, 'run', lambda *a, **k: SimpleNamespace(stdout=remote))
    flags = builder.updater_defines(release)
    assert f'-DHALO_BUILD_REPOSITORY=\\"{expected}\\"' in flags
    update = expected or 'OpenCommunityEdition/OpenCE'
    assert f'-DHALO_UPDATE_REPOSITORY=\\"{update}\\"' in flags
    assert '-DHALO_BUILD_NUMBER=87' in flags
    flavor = 'release' if release else 'debug'
    assert f'-DHALO_BUILD_FLAVOR=\\"{flavor}\\"' in flags
    assert 'secret' not in flags
