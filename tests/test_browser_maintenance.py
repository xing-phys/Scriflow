"""Behavioral regression tests for engine upgrades and advisory alerts; no live API writes."""
import io
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / 'scripts'))
import browser_engine as engine
import check_browser_security as security

CURRENT = '160.0.20261104181552'
NEW = '160.0.1.20261118081552'
REVISION = 'a' * 40
SOURCE = 'https://github.com/mozilla-firefox/firefox/commit/' + REVISION
NOTICE = b'Mozilla Public License 2.0 and third-party components'


def archive(path, version=CURRENT, notice=NOTICE):
    release, stamp = version.rsplit('.', 1)
    data = io.BytesIO()
    with zipfile.ZipFile(data, 'w') as omni:
        omni.writestr('modules/AppConstants.sys.mjs', 'MOZ_APP_VERSION: "' + release + '",\nMOZ_BUILDID: "' + stamp + '"')
        omni.writestr(engine.BUILD, '<a href="' + SOURCE + '">Source</a>')
        if notice is not None:
            omni.writestr(engine.LICENSE, notice)
    with zipfile.ZipFile(path, 'w') as outer:
        outer.writestr('assets/omni.ja', data.getvalue())


def link(identifier, product, singular=False):
    return '<a href="/en-US/security/advisories/' + identifier + '/"><span class="level high">MFSA ' + identifier[4:] + '</span> Security Vulnerabilit' + ('y' if singular else 'ies') + ' fixed in ' + product + '</a>'


class EngineTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(); self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / 'app').mkdir(); (self.root / 'licenses').mkdir()
        (self.root / 'app/build.gradle').write_text("implementation 'org.mozilla.geckoview:geckoview:" + CURRENT + "'\n")
        (self.root / 'licenses/geckoview-MPL-2.0.txt').write_text('Mozilla Public License 2.0')
        self.aar = self.root / 'engine.aar'; archive(self.aar)
        engine.refresh(CURRENT, self.aar, self.root)

    def test_refresh_and_verify_from_real_zip_metadata(self):
        engine.verify(self.aar, self.root)
        self.assertIn(SOURCE, (self.root / 'licenses/geckoview-SOURCE.txt').read_text())
        self.assertEqual(NOTICE, (self.root / 'licenses/geckoview-third-party.html').read_bytes())

    def test_upgrade_changes_all_provenance_together(self):
        archive(self.aar, NEW, NOTICE + b' updated')
        engine.refresh(NEW, self.aar, self.root)
        self.assertEqual(NEW, engine.pinned(self.root))
        engine.verify(self.aar, self.root)
        self.assertIn(NEW + '-sources.jar', (self.root / 'licenses/geckoview-SOURCE.txt').read_text())

    def test_version_only_dependabot_pr_is_rejected(self):
        file = self.root / 'app/build.gradle'; file.write_text(file.read_text().replace(CURRENT, NEW))
        with self.assertRaisesRegex(ValueError, 'pin and provenance differ'):
            engine.check(self.root)

    def test_tampered_archive_is_rejected(self):
        with self.aar.open('ab') as output: output.write(b'changed')
        with self.assertRaisesRegex(ValueError, 'checksum differs'):
            engine.verify(self.aar, self.root)

    def test_stale_notices_are_rejected(self):
        (self.root / 'licenses/geckoview-third-party.html').write_bytes(b'obsolete')
        with self.assertRaisesRegex(ValueError, 'notices differ'):
            engine.verify(self.aar, self.root)

    def test_wrong_source_archive_link_is_rejected(self):
        file = self.root / 'licenses/geckoview-SOURCE.txt'; file.write_text(file.read_text().replace('/archive/', '/wrong/'))
        with self.assertRaisesRegex(ValueError, 'provenance links differ'):
            engine.verify(self.aar, self.root)

    def test_failed_refresh_preserves_current_files(self):
        before = {p: p.read_bytes() for p in [self.root / 'app/build.gradle', *list((self.root / 'licenses').iterdir())]}
        archive(self.aar, NEW, None)
        with self.assertRaises(KeyError): engine.refresh(NEW, self.aar, self.root)
        self.assertTrue(all(p.read_bytes() == data for p, data in before.items()))

    def test_embedded_version_must_match_requested_version(self):
        with self.assertRaisesRegex(ValueError, 'inside the AAR'):
            engine.refresh(NEW, self.aar, self.root)

    def test_only_exact_stable_versions(self):
        for version in ['160.+', 'latest.release', '161.0a1.20261118081552', '160.0.20261399081552', '160.0-beta.1']:
            with self.subTest(version=version), self.assertRaises(ValueError): engine.version_parts(version)
        self.assertGreater(engine.version_parts(NEW), engine.version_parts(CURRENT))

    def test_maven_identity_and_version_order(self):
        xml = '<metadata><groupId>org.mozilla.geckoview</groupId><artifactId>geckoview</artifactId><versioning><versions>'
        xml += ''.join('<version>' + v + '</version>' for v in [CURRENT, '162.0a1.20261218081552', NEW])
        xml += '</versions></versioning></metadata>'
        self.assertEqual(NEW, engine.latest(xml))
        with self.assertRaises(ValueError): engine.latest(xml.replace('<artifactId>geckoview', '<artifactId>geckoview-nightly'))


class SecurityTests(unittest.TestCase):
    def test_current_and_older_advisories_do_not_alert(self):
        html = link('mfsa2026-1', 'Firefox 160') + link('mfsa2026-2', 'Firefox for Android 159.0.2')
        self.assertEqual([], security.candidates(html, CURRENT))

    def test_urgent_singular_and_patch_releases_alert(self):
        items = security.candidates(link('mfsa2026-3', 'Firefox 160.0.1', True), CURRENT)
        self.assertEqual('160.0.1', items[0]['fixed'])

    def test_android_and_combined_advisories_are_included(self):
        html = link('mfsa2026-4', 'Firefox for Android 160.0.2') + link('mfsa2026-5', 'Firefox 161, Firefox ESR 153.8, and Firefox for Android 160.0.3')
        items = security.candidates(html, CURRENT)
        self.assertEqual(['160.0.2', '161'], [v['fixed'] for v in items])

    def test_esr_ios_and_thunderbird_do_not_create_false_alerts(self):
        html = link('mfsa2026-1', 'Firefox 160') + ''.join(link('mfsa2026-' + str(i + 2), p) for i, p in enumerate(['Firefox ESR 200', 'Firefox for iOS 200', 'Thunderbird 200']))
        self.assertEqual([], security.candidates(html, CURRENT))

    def test_links_must_be_mozilla_advisories(self):
        html = link('mfsa2026-1', 'Firefox 160') + '<a href="https://other.example/mfsa2026-2/">Security Vulnerabilities fixed in Firefox 170</a>'
        self.assertEqual([], security.candidates(html, CURRENT))

    def test_broken_upstream_format_fails_visibly(self):
        for html in ['<html>Service unavailable</html>', link('mfsa2026-1', 'Firefox future')]:
            with self.subTest(html=html), self.assertRaises(ValueError): security.candidates(html, CURRENT)

    def test_metadata_severity_and_version_are_verified(self):
        item = security.candidates(link('mfsa2026-3', 'Firefox 161'), CURRENT)[0]
        yaml = 'impact: critical\nfixed_in:\n- Firefox 161\ntitle: A security fix\n'
        self.assertEqual('critical', security.advisory_impact(yaml, item))
        for bad in [yaml.replace('critical', 'unknown'), yaml.replace('Firefox 161', 'Firefox 159'), 'not metadata']:
            with self.subTest(bad=bad), self.assertRaises(ValueError): security.advisory_impact(bad, item)

    def test_dry_run_never_creates_alerts(self):
        item = {'id': 'mfsa2026-3', 'fixed': '161', 'product': 'Firefox 161', 'url': 'https://www.mozilla.org/en-US/security/advisories/mfsa2026-3/', 'impact': 'critical'}
        self.assertEqual('would open issue', security.report(CURRENT, [item])['actions'][0]['status'])

    def test_open_and_closed_advisories_are_deduplicated(self):
        class FakeGitHub:
            def __init__(self): self.created = []
            def existing(self): return {'mfsa2026-3', 'mfsa2026-4'}
            def create(self, item, bundled): self.created.append(item['id']); return {'html_url': 'https://github.com/example/repo/issues/1'}
        client = FakeGitHub()
        items = [dict(id='mfsa2026-' + str(i), impact='high') for i in [3, 4, 5, 5]]
        result = security.report(CURRENT, items, client)
        self.assertEqual(['mfsa2026-5'], client.created)
        self.assertEqual('already reviewed or tracked', result['actions'][0]['status'])

    def test_alert_body_keeps_review_and_signing_requirements(self):
        item = dict(id='mfsa2026-3', impact='critical', product='Firefox 161', url='https://www.mozilla.org/en-US/security/advisories/mfsa2026-3/')
        body = security.issue_body(item, CURRENT)
        self.assertIn(security.MARKER.format(item['id']), body)
        self.assertIn('potential engine security update', body)
        self.assertIn('scripts/build-release.sh', body)

    def test_issue_history_is_paginated_and_only_trusts_our_bot(self):
        class FakeAPI(security.GitHub):
            def __init__(self): super().__init__('example/repo', 'fake-token'); self.pages = []
            def request(self, path, data=None):
                self.pages.append(path)
                marker = lambda ident, author: {'body': security.MARKER.format(ident), 'user': {'login': author}}
                if path.endswith('&page=1'):
                    return [marker('mfsa2026-3', 'github-actions[bot]')] + [{'body': ''}] * 99
                return [marker('mfsa2026-4', 'github-actions[bot]'), marker('mfsa2026-5', 'outsider'),
                        dict(marker('mfsa2026-6', 'github-actions[bot]'), pull_request={})]
        api = FakeAPI()
        self.assertEqual({'mfsa2026-3', 'mfsa2026-4'}, api.existing())
        self.assertEqual(2, len(api.pages))

    def test_issue_creation_assigns_the_configured_owner(self):
        class FakeAPI(security.GitHub):
            def request(self, path, data=None): self.data = data; return {'html_url': 'https://github.com/example/repo/issues/1'}
        api = FakeAPI('example/repo', 'fake-token', 'xing-phys')
        item = dict(id='mfsa2026-3', impact='critical', product='Firefox 161', url='https://www.mozilla.org/en-US/security/advisories/mfsa2026-3/')
        api.create(item, CURRENT)
        self.assertEqual(['xing-phys'], api.data['assignees'])
        self.assertTrue(api.data['title'].startswith('[Critical]'))


if __name__ == '__main__':
    unittest.main()
