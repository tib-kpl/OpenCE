"""Build identity and the fork's updater must agree on their GitHub repository."""
from types import SimpleNamespace

import pytest

from tools import ci_build, linux_build, windows_build


@pytest.mark.parametrize('environment,expected', [
    ({'GITHUB_REF': 'refs/heads/main', 'GITHUB_RUN_NUMBER': '110'}, 110),
    ({'GITHUB_REF': 'refs/heads/main', 'GITHUB_RUN_NUMBER': '111', 'HALO_BUILD_NUMBER': '1'}, 111),
    ({'GITHUB_REF': 'refs/tags/v138', 'GITHUB_RUN_NUMBER': '110'}, 138),
    ({'GITHUB_REF': 'refs/heads/feature', 'GITHUB_RUN_NUMBER': '110'}, 0),
    ({'HALO_BUILD_NUMBER': '99'}, 99),
    ({}, 0),
])
def test_published_build_number(environment, expected):
    assert ci_build.build_number(environment) == expected


@pytest.mark.parametrize('run_number', ['', 'invalid', '0'])
def test_main_build_refuses_missing_release_number(run_number):
    with pytest.raises(ValueError, match='GITHUB_RUN_NUMBER'):
        ci_build.build_number({'GITHUB_REF': 'refs/heads/main', 'GITHUB_RUN_NUMBER': run_number})


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
