# Keeping the bundled browser current

Engine maintenance runs on GitHub, without adding polling or battery use to the
tablet. Installing a new signed Scriflow APK is how a new engine reaches it.

## Automatic checks

- **Every Monday at 08:15 Asia/Shanghai:** Dependabot checks the stable
  `org.mozilla.geckoview:geckoview` dependency in Mozilla's Maven repository.
  Update PRs are assigned to `xing-phys`. Beta and Nightly use different artifact
  names and are not selected. Other dependencies are outside this configuration.
  Mozilla's public Maven host is explicitly declared as an anonymous registry
  for Dependabot's network allowlist; it does not need registry credentials.
- **Daily at 08:23 Asia/Shanghai:** the **Browser security advisories** workflow
  checks Mozilla's advisory index. New Firefox or Firefox for Android fixes newer
  than the bundled engine's release version create assigned applicability-review
  issues, with the upstream severity in the title. Relevant critical fixes should
  receive an expedited release instead of waiting for the weekly cycle.
- ESR, iOS and Thunderbird advisories are excluded. Firefox desktop UI fixes can
  be irrelevant to GeckoView, so an alert requests review rather than claiming
  that Scriflow is definitely vulnerable. A zero-alert result means no newer
  matching advisories were found; it is not a security audit.
- Existing open or closed alerts from `github-actions[bot]` prevent duplicates.
  Reviewed issues are not reopened. Markers copied into unrelated public issues
  cannot suppress alerts. Close an alert after its fix ships, or record why it
  does not apply.

Use the repository's **Actions → Browser security advisories → Run workflow**
to check immediately. Each run records a summary; failures fetching or parsing
upstream data fail the job visibly. GitHub schedules may be delayed or disabled
on inactive repositories, so check the Actions page and keep GitHub notification
settings enabled for assignments and failed workflows. No email credentials or
third-party notification service are required.

## Reviewing an engine update

1. Check out the update PR. A version-only Dependabot PR intentionally fails
   `engine-policy` until its provenance and license inventory are refreshed.
2. Run `python3 scripts/browser_engine.py refresh`. It uses the exact version
   now pinned in Gradle, downloads the official AAR over HTTPS, checks its embedded
   version/build ID, and updates the checksum, source revision/links and full
   third-party license inventory together. To choose a specific stable release,
   add `--version EXACT_VERSION`. Use `latest` to inspect Mozilla's current stable
   metadata. `--aar PATH` reuses an already downloaded artifact.
3. Review the diff and commit it to the PR. The source link is extracted from the
   engine's own `buildconfig.html`; its version/build ID comes from
   `AppConstants.sys.mjs`. Downloaded code is never executed by this helper.
4. Pass CI. `engine-policy` checks the pin and recorded provenance and runs the
   maintenance regressions. The Android job runs the existing tests, lint and
   both builds, then verifies the resolved Gradle AAR's SHA-256, embedded source
   revision and notices. APK verification also checks the packaged notices.
5. Test rendering/math, website login persistence, microphone allow/deny,
   online-to-offline saving, offline confinement, floating notes/chats and
   rotation continuity. Keep Android 10/API 29 support; flag an upstream minimum
   Android change for review instead of raising it automatically. Review
   applicable Mozilla advisories and API changes.

Useful local commands:

```sh
python3 scripts/browser_engine.py latest
python3 scripts/browser_engine.py check
python3 -m unittest discover -s tests -p 'test_browser_maintenance.py'
python3 scripts/check_browser_security.py
```

The last command is read-only by default. Only `--write-issues` enables GitHub
changes, using `GITHUB_TOKEN`, `GITHUB_REPOSITORY` and an optional
`BROWSER_SECURITY_ASSIGNEE`. The scheduled workflow supplies an issues-only write
permission and checks out the trusted default branch. It never runs on PR events.

## Shipping the engine

After review and validation, use the existing release process in
[releasing.md](releasing.md). Choose the release version for its scope and
increase Android versionCode for every published APK. Build with
`scripts/build-release.sh` and the permanent signing key; the script verifies
engine provenance before staging any APK. For a custom Gradle cache, set
`GRADLE_USER_HOME` consistently for the build and verifier.

Publish the signed APK and checksum before requesting tablet testing. Do not
overwrite published tags/assets, auto-merge dependency PRs, or give PR workflows
access to release signing credentials. Monitoring and CI do not silently replace
the engine on the tablet.

References: [Mozilla GeckoView maintenance](https://wiki.mozilla.org/Mobile/GeckoView),
[Mozilla security advisories](https://www.mozilla.org/en-US/security/advisories/),
[Mozilla advisory source data](https://github.com/mozilla/foundation-security-advisories),
[Dependabot configuration](https://docs.github.com/en/code-security/concepts/supply-chain-security/about-the-dependabot-yml-file),
[GitHub scheduled workflows](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule).
