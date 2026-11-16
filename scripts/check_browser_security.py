#!/usr/bin/env python3
"""Daily Mozilla advisory review for the bundled engine. Default mode never writes to GitHub."""
import argparse
from datetime import datetime, timezone
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import re
import sys
import urllib.parse
import urllib.request

from browser_engine import fetch, pinned, version_parts

INDEX = 'https://www.mozilla.org/en-US/security/advisories/'
RAW = 'https://raw.githubusercontent.com/mozilla/foundation-security-advisories/main/announce/'
ID = re.compile(r'mfsa(\d{4})-\d{1,4}')
PRODUCT = re.compile(r'Firefox(?: for Android)? (\d+(?:\.\d+){0,2})')
MARKER = '<!-- scriflow-geckoview-advisory:{} -->'


def release_parts(value):
    if not re.fullmatch(r'\d+(?:\.\d+){0,2}', value):
        raise ValueError('Unrecognized Firefox release version: ' + value)
    parts = tuple(int(p) for p in value.split('.'))
    return parts + (0,) * (3 - len(parts))


class AdvisoryIndex(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.href = None
        self.text = []
        self.items = {}

    def handle_starttag(self, tag, attrs):
        if tag == 'a':
            self.href = dict(attrs).get('href', '')
            self.text = []

    def handle_data(self, text):
        if self.href is not None:
            self.text.append(text)

    def handle_endtag(self, tag):
        if tag != 'a' or self.href is None:
            return
        title = ' '.join(''.join(self.text).split())
        url = urllib.parse.urljoin(INDEX, self.href)
        parsed = urllib.parse.urlsplit(url)
        advisory = parsed.path.rstrip('/').rsplit('/', 1)[-1]
        if parsed.scheme == 'https' and parsed.netloc == 'www.mozilla.org' and ID.fullmatch(advisory):
            heading = re.search(r'\bSecurity Vulnerabilit(?:y|ies) fixed in (.+)$', title, re.I)
            if heading:
                product = heading[1]
                matches = list(re.finditer(r'Firefox(?: for Android)? (\d+(?:\.\d+){0,2})(?![\w.])', product))
                if matches:
                    match = max(matches, key=lambda item: release_parts(item[1]))
                    self.items[advisory] = {'id': advisory, 'fixed': match[1], 'url': url, 'product': match[0]}
                elif product.startswith('Firefox') and not product.startswith(('Firefox ESR ', 'Firefox for iOS ')):
                    raise ValueError('Mozilla changed its Firefox advisory format; manual review needed: ' + title)
        self.href = None


def candidates(html, bundled):
    index = AdvisoryIndex(); index.feed(html)
    if not index.items:
        raise ValueError('No Firefox advisories found. Upstream format or availability must be checked.')
    current = version_parts(bundled)[0]
    return sorted((item for item in index.items.values() if release_parts(item['fixed']) > current),
                  key=lambda item: (release_parts(item['fixed']), item['id']))


def advisory_impact(document, expected):
    # Only read these plain upstream YAML header fields. Do not deserialize or
    # execute YAML tags, and fail visibly if Mozilla changes their structure.
    impacts = re.findall(r'^impact: (critical|high|moderate|low)\s*$', document, re.M)
    fixed = re.search(r'^fixed_in:\s*\n((?:[ \t]*-[^\n]+\n)+)', document, re.M)
    if len(impacts) != 1 or fixed is None:
        raise ValueError('Advisory metadata format changed: ' + expected['id'])
    versions = []
    for line in fixed[1].splitlines():
        value = re.sub(r'^\s*-\s*', '', line).strip().strip('\"\'')
        versions.extend(release_parts(match[1]) for match in re.finditer(r'Firefox(?: for Android)? (\d+(?:\.\d+){0,2})(?![\w.])', value))
    if release_parts(expected['fixed']) not in versions:
        raise ValueError('Advisory index and fixed-version metadata disagree: ' + expected['id'])
    return impacts[0]


def issue_body(item, bundled):
    return f'''{MARKER.format(item['id'])}
Mozilla published a **{item['impact']}** advisory fixed in **{item['product']}**.
Scriflow currently pins **GeckoView {bundled}**.

Advisory: {item['url']}
Machine-readable upstream record: {RAW}{item['id'][4:8]}/{item['id']}.yml

This is a **potential engine security update requiring review**, not a claim
that every listed Firefox vulnerability affects GeckoView. Desktop UI-only
fixes may not apply. Check the advisory and Mozilla's stable GeckoView releases.

- [ ] Review GeckoView/Android applicability and expedite relevant critical fixes.
- [ ] Update the Gradle pin, AAR checksum, source links and notices with
  `python3 scripts/browser_engine.py refresh --version EXACT_STABLE_VERSION`.
- [ ] Pass CI and test rendering/math, login persistence, microphone consent,
  offline capture/reading, floating notes and portrait/landscape continuity.
- [ ] Increase Android versionCode and choose the appropriate release version.
- [ ] Build with `scripts/build-release.sh`, verify the permanent signer, and
  publish the signed APK and checksum before tablet testing.

Do not auto-merge or release this advisory's suggested upgrade. Close this issue
after the fix ships, or document why it does not affect Scriflow. This monitor
does not reopen an advisory that has already been reviewed and closed.
'''


class GitHub:
    def __init__(self, repository, token, assignee=None):
        if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository) or not token:
            raise ValueError('Writing alerts requires GITHUB_REPOSITORY and GITHUB_TOKEN.')
        self.repository, self.token = repository, token
        if assignee and not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9-]{0,38}', assignee):
            raise ValueError('Invalid browser security alert assignee.')
        self.assignee = assignee

    def request(self, path, data=None):
        url = 'https://api.github.com/repos/' + self.repository + path
        body = None if data is None else json.dumps(data).encode()
        request = urllib.request.Request(url, data=body, headers={
            'Authorization': 'Bearer ' + self.token, 'Accept': 'application/vnd.github+json',
            'Content-Type': 'application/json', 'User-Agent': 'Scriflow-security-monitor',
            'X-GitHub-Api-Version': '2022-11-28'})
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)

    def existing(self):
        found = set()
        for page in range(1, 101):
            items = self.request('/issues?state=all&per_page=100&page=' + str(page))
            for item in items:
                # Public issue authors cannot suppress future security alerts by
                # copying our marker. Only this workflow's own alerts count.
                if 'pull_request' not in item and item.get('user', {}).get('login') == 'github-actions[bot]':
                    found.update(re.findall(r'<!-- scriflow-geckoview-advisory:(mfsa\d{4}-\d{1,4}) -->', item.get('body') or ''))
            if len(items) < 100:
                return found
        raise ValueError('Issue history pagination limit reached; do not risk duplicate alerts.')

    def create(self, item, bundled):
        data = {'title': '[' + item['impact'].capitalize() + '] Review Mozilla ' + item['id']
                + ' for bundled GeckoView', 'body': issue_body(item, bundled)}
        if self.assignee:
            data['assignees'] = [self.assignee]
        return self.request('/issues', data)


def report(bundled, items, client=None):
    existing = client.existing() if client is not None and items else set()
    actions = []
    for item in items:
        if item['id'] in existing:
            actions.append({'id': item['id'], 'status': 'already reviewed or tracked'})
        elif client is None:
            actions.append({'id': item['id'], 'status': 'would open issue', 'impact': item['impact']})
        else:
            issue = client.create(item, bundled)
            actions.append({'id': item['id'], 'status': 'opened issue', 'url': issue['html_url']})
            existing.add(item['id'])
    return {'checked_at': datetime.now(timezone.utc).isoformat(), 'bundled': bundled,
            'advisories_requiring_review': len(items), 'actions': actions}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write-issues', action='store_true', help='Explicitly enable deduplicated repository alerts.')
    parser.add_argument('--output', type=Path, help='Optional JSON report path.')
    args = parser.parse_args()
    try:
        bundled = pinned()
        items = candidates(fetch(INDEX).decode('utf-8'), bundled)
        for item in items:
            document = fetch(RAW + item['id'][4:8] + '/' + item['id'] + '.yml').decode('utf-8')
            item['impact'] = advisory_impact(document, item)
        client = GitHub(os.environ.get('GITHUB_REPOSITORY', ''), os.environ.get('GITHUB_TOKEN', ''),
                        os.environ.get('BROWSER_SECURITY_ASSIGNEE')) if args.write_issues else None
        result = report(bundled, items, client)
        text = json.dumps(result, indent=2)
        print(text)
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True); args.output.write_text(text + '\n')
        summary = os.environ.get('GITHUB_STEP_SUMMARY')
        if summary:
            with open(summary, 'a') as output:
                output.write('### Browser security check\n\nBundled GeckoView: `' + bundled + '`\n\n')
                output.write(str(len(items)) + ' newer Firefox/Android advisories require applicability review.\n')
                for action in result['actions']:
                    output.write('- ' + action['id'] + ': ' + action['status'] + (' — ' + action['url'] if 'url' in action else '') + '\n')
        return 0
    except (ValueError, OSError, KeyError, json.JSONDecodeError) as error:
        print('Browser security check failed (not a clean security result): ' + str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
